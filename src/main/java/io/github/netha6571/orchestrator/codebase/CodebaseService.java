package io.github.netha6571.orchestrator.codebase;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for reading an external codebase. Agents call into this
 * to inspect the project they are working on — its structure, source
 * files, and whether a given path exists.
 *
 * Read-only for now. Writing generated code back to disk is a separate
 * concern that belongs in a future apply/patch step.
 */
public interface CodebaseService {

    /**
     * The absolute path to the project root.
     */
    String rootPath();

    /**
     * A tree-style listing of the project's files and directories,
     * filtered to source-relevant content. Noise directories like
     * .git, target, node_modules are excluded.
     *
     * The result is a human-readable string suitable for inclusion
     * in an LLM prompt.
     */
    String getProjectStructure();

    /**
     * Read the contents of a single file.
     *
     * @param relativePath path relative to the project root
     * @return the file contents as a string
     * @throws CodebaseException if the file does not exist or cannot be read
     */
    String readFile(String relativePath);

    /**
     * List all files whose name ends with the given extension.
     * The returned paths are relative to the project root.
     *
     * @param extension e.g. ".java", ".py", ".ts"
     * @return list of matching relative paths, never null
     */
    List<String> listFiles(String extension);

    /**
     * Check whether a file exists at the given relative path.
     */
    boolean fileExists(String relativePath);

    /**
     * Read multiple files in one call. Paths that do not exist or
     * cannot be read are silently skipped.
     *
     * @param relativePaths paths relative to the project root
     * @return map of relativePath → file contents
     */
    Map<String, String> readFiles(List<String> relativePaths);
}
