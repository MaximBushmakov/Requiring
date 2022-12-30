# Requiring
HW 2 for Java <p>
Given root directory (by args[0] in main method or in command line).
Files in the directory may contain lines in the format "Require "relative/path/to/file"".
Required to create file with condition: if file T requires file B then file T locates below file A in output.
Output file is created by concatenating of files in the given order. <p>
If there is loop dependence, program writes loop files to standard output.
