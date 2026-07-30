package com.org.orchestrator.codebase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads an external codebase from the local filesystem.
 *
 * Filters out noise directories (.git, target, node_modules, etc.)
 * and applies size guards so large binary files don't get stuffed
 * into LLM prompts.
 */
public final class FileSystemCodebaseService implements CodebaseService {

    /** Directories to skip when walking the tree. */
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".hg",
            "target", "build", "out", "dist", "bin",
            "node_modules", ".gradle", ".mvn",
            ".idea", ".vscode", ".settings", ".eclipse",
            "__pycache__", ".tox", ".mypy_cache",
            "venv", ".venv", "env"
    );

    /** Maximum depth when building the project tree. */
    private static final int MAX_TREE_DEPTH = 5;

    /** Maximum file size (in bytes) that readFile will return. */
    private static final long MAX_FILE_SIZE = 100_000; // 100 KB

    private final Path root;

    public FileSystemCodebaseService(Path root) {
        if (root == null) {
            throw new CodebaseException("Root path must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            throw new CodebaseException("Not a directory: " + this.root);
        }
    }

    @Override
    public String rootPath() {
        return root.toString();
    }

    @Override
    public String getProjectStructure() {
        StringBuilder sb = new StringBuilder();
        sb.append(root.getFileName()).append("/\n");
        buildTree(root, "", 0, sb);
        return sb.toString();
    }

    @Override
    public String readFile(String relativePath) {
        Path resolved = resolveSafely(relativePath);
        if (!Files.isRegularFile(resolved)) {
            throw new CodebaseException("Not a file: " + relativePath);
        }
        try {
            long size = Files.size(resolved);
            if (size > MAX_FILE_SIZE) {
                return "// [File too large: " + size + " bytes, skipped]\n";
            }
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new CodebaseException("Cannot read file: " + relativePath, e);
        }
    }

    @Override
    public List<String> listFiles(String extension) {
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> !isInsideSkippedDir(p))
                .filter(p -> p.getFileName().toString().endsWith(extension))
                .forEach(p -> result.add(root.relativize(p).toString()));
        } catch (IOException e) {
            throw new CodebaseException("Cannot walk project tree", e);
        }
        return result;
    }

    @Override
    public boolean fileExists(String relativePath) {
        Path resolved = resolveSafely(relativePath);
        return Files.isRegularFile(resolved);
    }

    @Override
    public Map<String, String> readFiles(List<String> relativePaths) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : relativePaths) {
            try {
                result.put(path, readFile(path));
            } catch (CodebaseException e) {
                // Silently skip unreadable files — caller asked for best-effort.
            }
        }
        return result;
    }

    // --- Internals ---

    /**
     * Resolve a relative path against the root, preventing path traversal
     * attacks (e.g. "../../etc/passwd").
     */
    private Path resolveSafely(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new CodebaseException("Path must not be blank");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new CodebaseException(
                    "Path escapes project root: " + relativePath);
        }
        return resolved;
    }

    /**
     * Check whether any ancestor directory of a path is in the skip set.
     */
    private boolean isInsideSkippedDir(Path path) {
        Path relative = root.relativize(path);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (SKIP_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively build an indented tree string, skipping noise dirs
     * and stopping at MAX_TREE_DEPTH.
     */
    private void buildTree(Path dir, String indent, int depth, StringBuilder sb) {
        if (depth >= MAX_TREE_DEPTH) {
            sb.append(indent).append("  ...\n");
            return;
        }

        List<Path> children;
        try (Stream<Path> stream = Files.list(dir)) {
            children = stream.sorted().toList();
        } catch (IOException e) {
            sb.append(indent).append("  [unreadable]\n");
            return;
        }

        for (Path child : children) {
            String name = child.getFileName().toString();

            if (Files.isDirectory(child)) {
                if (SKIP_DIRS.contains(name)) {
                    continue; // skip noise
                }
                sb.append(indent).append("  ").append(name).append("/\n");
                buildTree(child, indent + "  ", depth + 1, sb);
            } else {
                sb.append(indent).append("  ").append(name).append("\n");
            }
        }
    }
}
