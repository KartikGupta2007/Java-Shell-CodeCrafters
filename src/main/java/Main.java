import java.util.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final Set<String> BUILTINS = Set.of("exit", "echo", "type", "pwd", "cd");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            String cmd = sc.nextLine();

            if(cmd.isBlank()){
                continue;
            }

            ParsedCommand parsedCommand = parseCommand(cmd);
            List<String> commandParts = parsedCommand.arguments;
            if (commandParts.isEmpty()) {
                continue;
            }

            String commandName = commandParts.get(0);
            String redirectTarget = parsedCommand.redirectTarget;
            String stderrRedirectTarget = parsedCommand.stderrRedirectTarget;

            prepareRedirectTarget(redirectTarget);
            prepareRedirectTarget(stderrRedirectTarget);

            if(commandName.equals("exit") && (commandParts.size() == 1 || commandParts.get(1).equals("0"))){
                break;
            }
            else if(commandName.equals("echo")){
                writeOutput(String.join(" ", commandParts.subList(1, commandParts.size())) + System.lineSeparator(), redirectTarget);
            }
            else if(commandName.equals("pwd")){
                writeOutput(currentDirectory.getAbsolutePath() + System.lineSeparator(), redirectTarget);
            }
            else if(commandName.equals("cd") && commandParts.size() > 1){
                String directory = commandParts.get(1);
                String targetDirectory = directory.equals("~") ? System.getenv("HOME") : directory;
                File newDirectory = targetDirectory == null ? null : new File(targetDirectory);
                if (newDirectory != null && !newDirectory.isAbsolute()) {
                    newDirectory = new File(currentDirectory, directory);
                }

                if (newDirectory != null && newDirectory.isDirectory()) {
                    currentDirectory = newDirectory.getCanonicalFile();
                } else {
                    writeError("cd: " + directory + ": No such file or directory" + System.lineSeparator(), parsedCommand.stderrRedirectTarget);
                }
            }
            else if(commandName.equals("type") && commandParts.size() > 1){
                String sub = commandParts.get(1);
                if(BUILTINS.contains(sub)){
                    writeOutput(sub + " is a shell builtin" + System.lineSeparator(), redirectTarget);
                }
                else {
                    File executable = findExecutable(sub);
                    if (executable != null) {
                        writeOutput(sub + " is " + executable.getAbsolutePath() + System.lineSeparator(), redirectTarget);
                    } else {
                        writeError(sub + ": not found" + System.lineSeparator(), parsedCommand.stderrRedirectTarget);
                    }
                }
            }
            else {
                File executable = findExecutable(commandName);
                if (executable != null) {
                    ProcessBuilder processBuilder = new ProcessBuilder(commandParts)
                            .directory(currentDirectory)
                            .redirectError(ProcessBuilder.Redirect.INHERIT);

                    if (redirectTarget != null) {
                        processBuilder.redirectOutput(new File(redirectTarget));
                    } else {
                        processBuilder.inheritIO();
                    }

                    if (stderrRedirectTarget != null) {
                        processBuilder.redirectError(new File(stderrRedirectTarget));
                    }

                    Process process = processBuilder.start();
                    process.waitFor();
                } else {
                    writeError(cmd + ": command not found" + System.lineSeparator(), stderrRedirectTarget);
                }
            }
        }
    }

    private static void prepareRedirectTarget(String redirectTarget) throws Exception {
        if (redirectTarget != null) {
            Files.writeString(Path.of(redirectTarget), "");
        }
    }

    private static void writeOutput(String output, String redirectTarget) throws Exception {
        if (redirectTarget != null) {
            Files.writeString(Path.of(redirectTarget), output);
        } else {
            System.out.print(output);
        }
    }

    private static void writeError(String output, String redirectTarget) throws Exception {
        if (redirectTarget != null) {
            Files.writeString(Path.of(redirectTarget), output);
        } else {
            System.err.print(output);
        }
    }

    private static ParsedCommand parseCommand(String command) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasCurrentArg = false;

        for (int i = 0; i < command.length(); i++) {
            char currentChar = command.charAt(i);

            if (currentChar == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                hasCurrentArg = true;
            } else if (currentChar == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                hasCurrentArg = true;
            } else if (currentChar == '\\' && inDoubleQuotes && i + 1 < command.length()) {
                char nextChar = command.charAt(i + 1);
                if (nextChar == '"' || nextChar == '\\') {
                    currentArg.append(nextChar);
                    i++;
                } else {
                    currentArg.append(currentChar);
                }
                hasCurrentArg = true;
            } else if (currentChar == '\\' && !inSingleQuotes && !inDoubleQuotes && i + 1 < command.length()) {
                currentArg.append(command.charAt(i + 1));
                hasCurrentArg = true;
                i++;
            } else if (Character.isWhitespace(currentChar) && !inSingleQuotes && !inDoubleQuotes) {
                if (hasCurrentArg) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                    hasCurrentArg = false;
                }
            } else {
                currentArg.append(currentChar);
                hasCurrentArg = true;
            }
        }

        if (hasCurrentArg) {
            args.add(currentArg.toString());
        }

        String redirectTarget = null;
        String stderrRedirectTarget = null;
        List<String> filteredArgs = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ((arg.equals(">") || arg.equals("1>")) && i + 1 < args.size()) {
                redirectTarget = args.get(i + 1);
                i++;
            } else if (arg.equals("2>") && i + 1 < args.size()) {
                stderrRedirectTarget = args.get(i + 1);
                i++;
            } else {
                filteredArgs.add(arg);
            }
        }

        return new ParsedCommand(filteredArgs, redirectTarget, stderrRedirectTarget);
    }

    private static class ParsedCommand {
        private final List<String> arguments;
        private final String redirectTarget;
        private final String stderrRedirectTarget;

        private ParsedCommand(List<String> arguments, String redirectTarget, String stderrRedirectTarget) {
            this.arguments = arguments;
            this.redirectTarget = redirectTarget;
            this.stderrRedirectTarget = stderrRedirectTarget;
        }
    }

    private static File findExecutable(String command) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }

        String[] pathDirs = path.split(File.pathSeparator);
        for (String dir : pathDirs) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }
}
