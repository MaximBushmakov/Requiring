/*
 * Requiring.java     1.0     22/12/30
 * No copyright license
 */

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * Output file is located at "<code>../Output.txt</code>". <p>
 * Requires files in UTF-8 encoding.
 *
 * @author Maxim Bushmakov
 */
public class Requiring {
    /* Method main finds number of files to set size of states
     *  Then finds from which files chains of requires are started
     *  Then it writes files' content in order using method writeOut
     *  If there are chains without root (no FREE state file) there is a loop, so it can be found with writeOut
     *  If there is a loop its members are printed and written data is deleted
     *  writeOut method uses static members states and rootPath what mean high coupling and should be refactored */

    /**
     * FREE - file is not required anywhere (before processing) <p>
     * DEPENDENT - file is required somewhere (before processing) <p>
     * IN_PROCESS - file is process (using to find loops in require chain) <p>
     * WRITTEN - file is already written to output (after processing)
     */
    private enum FILE_STATE {FREE, DEPENDENT, IN_PROCESS, WRITTEN}

    /**
     * Map from file path to its FILE_STATE
     */
    private static HashMap<Path, FILE_STATE> states;

    /* Required to get absolute path from path relative to the root directory */
    private static Path rootPath;

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

        rootPath = Path.of(root);

        final File rootFile = new File(root);
        if (!rootFile.exists()) {
            throw new IOException("Invalid directory");
        }

        final int filesNum = getFilesNum(rootFile);

        states = new HashMap<>(2 * filesNum);

        /* before processing phase
        this code won't be parallelized in a good way, all blocks at states
        it throws RuntimeException with IOException as cause to rethrow it later */
        try (final Stream<Path> directories = Files.walk(rootPath)) {
            directories.filter(directory -> directory.toFile().isFile()).forEach(curPath -> {

                final Iterable<Path> requiredPaths;
                try {
                    requiredPaths = getRequires(curPath);
                } catch (IOException e) {
                    throw new RuntimeException("",
                            new IOException("Can't open file " + curPath));
                }

                for (Path reqPath : requiredPaths) {
                    Path reqPathAbs = rootPath.resolve(reqPath).normalize();
                    if (reqPathAbs.toFile().isFile()) {
                        states.put(reqPath, FILE_STATE.DEPENDENT);
                    } else {
                        throw new RuntimeException("",
                                new IOException("Can't find file " + reqPath.normalize() + " required in file " + curPath));
                    }
                }

                // stores relative paths
                states.putIfAbsent(rootPath.relativize(curPath).normalize(), FILE_STATE.FREE);
            });
        } catch (RuntimeException e) {
            if (e.getCause().getClass() == IOException.class) {
                throw (IOException) e.getCause();
            } else {
                throw e;
            }
        }

        // create (or not) and get output file
        File outputFile = rootPath.resolve("../Output.txt").toFile();
        if (!outputFile.isFile()) {
            Files.createFile(rootPath.resolve("../Output.txt"));
        }

        /* processing phase */
        // res contains result of writeOut
        Path res = null;
        try (var output = new FileWriter(outputFile, false)) {
            for (final var entry : states.entrySet()) {
                if (entry.getValue() == FILE_STATE.FREE) {
                    res = writeOut(entry.getKey(), output);
                    if (res != null) break;
                }
            }
        }

        // check if all files been written
        // if there are some that not - there must be a loop
        if (res == null) {
            try (var output = new FileWriter(outputFile, true)) {
                for (var entry : states.entrySet()) {
                    if (entry.getValue() != FILE_STATE.WRITTEN) {
                        res = writeOut(entry.getKey(), output);
                        if (res != null) break;
                    }
                }
            }
        }

        if (res != null) {
            System.out.println("Found loop in requires:");

            Path cur = res;
            do {
                System.out.println(cur + " requires");
                for (var entry : states.entrySet()) {
                    if (entry.getValue() == FILE_STATE.IN_PROCESS) {
                        cur = entry.getKey();
                        break;
                    }
                }
            } while (!cur.equals(res));
            System.out.println(res);

            // clear output file
            try (var output = new FileWriter(outputFile, false)) {
                output.write("");
            }
        }
    }

    /**
     * Calculates number of files in the directory
     * (including those in nested folders)
     *
     * @param cur path to the directory (root)
     * @return number of files in the directory
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
    private static @NotNull Iterable<Path> getRequires(final @NotNull Path path) throws IOException {
        var requires = new Vector<Path>();
        try (final Stream<String> stream = Files.lines(path)) {
            stream.forEach((line) -> {
                if (line.startsWith("Require \"") && line.endsWith("\"")) {
                    requires.add(Path.of(line.substring(9, line.length() - 1)));
                }
            });
        }
        return requires;
    }

    /**
     * Writes all required files from the given file to output
     * then writes given file's content to output. <p>
     * Uses states so must be called after states is initialized.
     * Uses deep first traversal. <p>
     * If found loop returns false, loop vertices have IN_PROCESS state.
     *
     * @param path   path to the file
     * @param output writer for output file
     * @return path to file with error (one of vertices in loop)
     * @throws IOException if I/O error occurs while writing
     */
    private static @Nullable Path writeOut(final @NotNull Path path, final @NotNull FileWriter output) throws IOException {
        states.replace(path, FILE_STATE.IN_PROCESS);
        for (Path next : getRequires(rootPath.resolve(path).normalize())) {
            switch (states.get(next.normalize())) {
                case IN_PROCESS -> {
                    return path;
                }
                case WRITTEN -> {
                    // do nothing
                }
                default -> {
                    Path err = writeOut(next, output);
                    if (err != null) {
                        return err;
                    }
                }
            }
        }
        // there must be more primitive way to copy file's content
        output.write(Files.readString(rootPath.resolve(path).normalize()));
        output.write("\n");
        states.replace(path, FILE_STATE.WRITTEN);
        return null;
    }
}