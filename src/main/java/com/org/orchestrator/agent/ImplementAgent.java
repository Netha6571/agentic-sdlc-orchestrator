package com.org.orchestrator.agent;

import com.org.orchestrator.codebase.CodebaseService;
import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.WorkflowContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the code change from the design. High impact — always requires
 * approval no matter which path (model or fallback) produced the output.
 *
 * When a CodebaseService is present, the agent reads the target project's
 * structure and source files so the LLM can produce changes grounded in
 * the real codebase. Without one, it works the same as before.
 */
public final class ImplementAgent extends AbstractAgent {

    public static final StageId ID = new StageId("implement");

    /**
     * Maximum total characters of source content to include in the prompt.
     * Prevents blowing past context limits on large codebases.
     */
    private static final int SOURCE_BUDGET = 60_000;

    private final CodebaseService codebase;

    /**
     * Create an ImplementAgent without codebase access (original behaviour).
     */
    public ImplementAgent(LlmClient llmClient) {
        this(llmClient, null);
    }

    /**
     * Create an ImplementAgent with access to an external codebase.
     */
    public ImplementAgent(LlmClient llmClient, CodebaseService codebase) {
        super(ID, llmClient);
        this.codebase = codebase;
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        String design = lastEntry(context, "design", "design");
        String spec = lastEntry(context, "requirement", "spec");

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a software engineer. ");
        prompt.append("Implement the following design as a code change.\n\n");
        prompt.append("Specification:\n").append(spec).append("\n\n");
        prompt.append("Design:\n").append(design).append("\n\n");

        // --- Codebase context ---
        if (codebase != null) {
            prompt.append("=== TARGET PROJECT ===\n");
            prompt.append("Root: ").append(codebase.rootPath()).append("\n\n");
            prompt.append("Project structure:\n");
            prompt.append(codebase.getProjectStructure()).append("\n");

            // Include source files up to the budget.
            appendSourceFiles(prompt);

            prompt.append("=== END TARGET PROJECT ===\n\n");

            // Structured output instructions.
            prompt.append(STRUCTURED_OUTPUT_INSTRUCTIONS);
        }

        return prompt.toString();
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty implementation response");
        }

        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("code", response.strip());

        // If the response contains structured file blocks, extract them.
        if (response.contains("### FILE:")) {
            outputs.put("change_plan", extractChangePlan(response));
        }

        return outputs;
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        String design = lastEntry(context, "design", "design");

        StringBuilder skeleton = new StringBuilder();
        skeleton.append("// Implementation skeleton based on design\n");
        skeleton.append("// Design: ").append(truncate(design, 150)).append("\n");

        if (codebase != null) {
            skeleton.append("// Target project: ").append(codebase.rootPath()).append("\n");
            skeleton.append("//\n");
            skeleton.append("// Project structure:\n");
            for (String line : codebase.getProjectStructure().split("\n")) {
                skeleton.append("//   ").append(line).append("\n");
            }
            skeleton.append("//\n");
        }

        skeleton.append("public class ChangeImpl {\n");
        skeleton.append("    public void apply() {\n");
        skeleton.append("        // TODO: fill in from design\n");
        skeleton.append("        System.out.println(\"Change applied\");\n");
        skeleton.append("    }\n");
        skeleton.append("}\n");

        return Map.of("code", skeleton.toString());
    }

    // Implement is high impact — always needs sign-off.
    @Override
    protected boolean requiresApproval() {
        return true;
    }

    // --- Internals ---

    /**
     * Append source files to the prompt, stopping when the character
     * budget is exhausted so we don't blow the context window.
     */
    private void appendSourceFiles(StringBuilder prompt) {
        // Collect source files for common languages.
        List<String> sourceFiles = collectSourceFiles();

        if (sourceFiles.isEmpty()) {
            prompt.append("(no source files found)\n\n");
            return;
        }

        prompt.append("Source files:\n\n");
        int charsBudget = SOURCE_BUDGET;

        for (String path : sourceFiles) {
            if (charsBudget <= 0) {
                prompt.append("... (remaining files omitted — context budget reached)\n");
                break;
            }

            String content = codebase.readFile(path);
            int cost = path.length() + content.length() + 30; // overhead for markers

            if (cost > charsBudget && charsBudget < SOURCE_BUDGET) {
                // Don't include a file that would exceed the remaining budget,
                // unless it's the very first file (always include at least one).
                prompt.append("... (remaining files omitted — context budget reached)\n");
                break;
            }

            prompt.append("--- ").append(path).append(" ---\n");
            prompt.append(content).append("\n\n");
            charsBudget -= cost;
        }
    }

    /**
     * Collect source files from the codebase, trying common extensions
     * in priority order.
     */
    private List<String> collectSourceFiles() {
        // Try common source extensions in order of likelihood.
        String[] extensions = {
                ".java", ".py", ".ts", ".js", ".go", ".rs",
                ".kt", ".scala", ".rb", ".cs", ".cpp", ".c", ".h"
        };

        List<String> all = new java.util.ArrayList<>();
        for (String ext : extensions) {
            List<String> found = codebase.listFiles(ext);
            all.addAll(found);
        }

        // Also include config files that are usually important.
        String[] configFiles = {
                "pom.xml", "build.gradle", "package.json", "Cargo.toml",
                "go.mod", "requirements.txt", "pyproject.toml",
                "tsconfig.json", "Makefile", "Dockerfile"
        };
        for (String cfg : configFiles) {
            if (codebase.fileExists(cfg) && !all.contains(cfg)) {
                all.add(0, cfg); // config files first — they give project context
            }
        }

        return all;
    }

    /**
     * Extract a compact change plan summary from structured file blocks
     * in the LLM response.
     */
    private static String extractChangePlan(String response) {
        StringBuilder plan = new StringBuilder();
        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("### FILE:") || trimmed.startsWith("### ACTION:")) {
                plan.append(trimmed).append("\n");
            }
        }
        return plan.length() > 0 ? plan.toString().strip() : "(no structured plan extracted)";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // --- Prompt template for structured output ---

    private static final String STRUCTURED_OUTPUT_INSTRUCTIONS = """
            Based on the existing codebase above, produce your implementation as a list
            of file-level changes. For each file, use this format:

            ### ACTION: ADD | UPDATE | REMOVE
            ### FILE: <relative path from project root>
            ```
            <full file content for ADD, or updated content for UPDATE, or leave empty for REMOVE>
            ```
            ### REASON: <one-line explanation of why this file is changed>

            Rules:
            - For UPDATE, include the complete new file content (not a diff).
            - For ADD, include the full file content.
            - For REMOVE, leave the code block empty.
            - Respect the project's existing package structure, naming conventions,
              and coding style.
            - Only change files that are necessary for the requirement.
            """;
}
