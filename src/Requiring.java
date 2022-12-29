/*
 * Requiring.java     0.2     22/12/29
 * No copyright license
 */

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Vector;
import java.util.stream.Stream;

/**
 * Main class <p>
 * Creates file containing content of all files in the given directory (root) <p>
 * Each file in the directory may contain lines in the format {Require "PATH"}.
 * PATH is a path from the root directory, using '/' as separator between folders.
 * In the output each file's content must be written below all files it requires.
 * Output file is located in the given directory and named "Output.txt". <p>
 * Requires files in UTF-8 encoding.
 *
 * @author Maxim Bushmakov
 */
// TODO main
public class Requiring {
    // TODO comment
    /* Class impl comment */

    /**
     * FREE - file is not required anywhere (before processing) <p>
     * DEPENDENT - file is required somewhere (before processing) <p>
     * IN_PROCESS - file is process (using to find loops in require chain) <p>
     * WRITTEN - file is already written to output (after processing)
     */
    private enum FILE_STATE {FREE, DEPENDENT, IN_PROCESS, WRITTEN}

    /**
     * Uses DFS-like algorithm for oriented graph of files (require statement is edge)
     * that is supposed to be a tree
     * to write out files from bottom before files from above.
     * While writing also checking for loop.
     *
     * @param args if args is not null args[0] is the path to the root directory
     * @throws IOException if given directory is incorrect or one of files cannot be opened
     * @see Requiring
     */
    public static void main(final String @NotNull [] args) throws IOException {

        final String root;
        if (args.length == 0) {
            root = (new Scanner(System.in)).nextLine();
        } else {
            root = args[0];
        }

        final File rootFile = new File(root);
        if (!rootFile.exists()) {
            throw new IOException("Invalid directory");
        }

        Path rootPath = Path.of(root);
        final File outputFile = rootPath.resolve("Output.txt").toFile();
        try (var writer = new FileWriter(outputFile, false)) {
            writer.write("");
        }

        final int filesNum = getFilesNum(rootFile);

        /* Map from file path to its FILE_STATE */
        var states = new HashMap<String, FILE_STATE>(2 * filesNum);

        /* before processing phase
        this code won't parallelize in a good way, all blocks at states
        it throws RuntimeException with IOException as cause to rethrow it later */
        try (final Stream<Path> directories = Files.walk(rootPath)) {
            directories.filter(directory -> directory.toFile().isFile()).forEach(curPath -> {

                final Iterable<String> requiredPaths;
                try {
                    requiredPaths = getRequires(curPath);
                } catch (IOException e) {
                    throw new RuntimeException("", new IOException("Can't open file " + curPath));
                }

                for (String path : requiredPaths) {
                    Path pathAbs = rootPath.resolve(Path.of(path));
                    if (pathAbs.toFile().isFile()) {
                        states.put(path, FILE_STATE.DEPENDENT);
                    } else {
                        throw new RuntimeException("", new IOException("Can't find file " + pathAbs + " required in file " + curPath));
                    }
                }

                // stores relative paths
                states.putIfAbsent(String.valueOf(rootPath.relativize(curPath)), FILE_STATE.FREE);
            });
        } catch (RuntimeException e) {
            if (e.getCause().getClass() == IOException.class) {
                throw (IOException) e.getCause();
            } else {
                throw e;
            }
        }

        /* processing phase */
        for (var entry : states.entrySet()) {
            if (entry.getValue() == FILE_STATE.FREE) {
                writeOut(rootPath.resolve(Path.of(entry.getKey())).normalize(), outputFile);
            }
        }

        for (var entry : states.entrySet()) {
            if (entry.getValue() != FILE_STATE.WRITTEN) {
                // Huston, we have problems
            }
        }
    }

    /**
     * Calculates number of files in the directory
     *
     * @param cur path to the directory (root)
     * @return number of files in the directory (including those in nested folders)
     * @throws IOException if given invalid directory or in case of I/O errors
     */
    @Contract(pure = true)
    private static int getFilesNum(final @NotNull File cur) throws IOException {
        File[] directories = cur.listFiles();
        if (directories == null) {
            throw new IOException("Can't open directory " + cur);
        }

        if (directories.length == 0) {
            return 0;
        }

        int filesNum = 0;
        for (final File next : directories) {
            if (next.isDirectory()) {
                filesNum += getFilesNum(next);
            } else {
                ++filesNum;
            }
        }
        return filesNum;
    }

    /**
     * Scans given file for lines in format [Require "PATH"] and returns array of PATHs.
     * PATHs are not get checked to be a real path to a file.
     *
     * @param path path to file in which require statements are seek
     * @return paths to required files (as they are written)
     * @throws IOException if file cannot be opened
     */
    @Contract(value = "_ -> new", pure = true)
    private static @NotNull Iterable<String> getRequires(final @NotNull Path path) throws IOException {
        var requires = new Vector<String>();
        try (final Stream<String> stream = Files.lines(path)) {
            stream.forEach((line) -> {
                if (line.startsWith("Require \"") && line.endsWith("\"")) {
                    requires.add(line.substring(9, line.length() - 1));
                }
            });
        }
        return requires;
    }

    private static void writeOut(final @NotNull Path path, final @NotNull File outputFile) {

    }
}