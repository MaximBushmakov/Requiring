/*
 * Requiring.java     0.1     22/12/29
 * No copyright license
 */

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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
 * In the output each file's content must be written below all files it requires. <p>
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

        final int filesNum = getFilesNum(rootFile);

        /* Map from file path to its FILE_STATE */
        var states = new HashMap<String, FILE_STATE>(2 * filesNum);

        /* store all file paths to states */
        // using Files is a very interesting way to slow down the program
        Path rootPath = Path.of(root);
        try (final Stream<Path> files = Files.walk(rootPath)) {
            for (final Object file : files.toArray()) {
                if (((Path) file).toFile().isFile()) {
                    for (final String path : getRequires((Path) file)) {
                        states.put(path, FILE_STATE.DEPENDENT);
                    }
                    // stores relative paths
                    states.putIfAbsent(String.valueOf(((Path) file).relativize(rootPath)), FILE_STATE.FREE);
                }
            }
        }

        // create stack for files' numbers
        // put free files in stack
        // if there is no files find loop
        // else (go through, check for loop, write file) while stack is not empty

    }

    /**
     * Calculates number of files in the directory
     *
     * @param cur path to the directory (root)
     * @return number of files in the directory (including those in nested folders)
     */
    @Contract(pure = true)
    private static int getFilesNum(final @NotNull File cur) {
        int filesNum = 0;
        // as far as path is not null, path.listFiles() is not null too
        for (final File next : cur.listFiles()) {
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
}