import java.util.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final Set<String> BUILTINS = Set.of("exit", "echo", "type", "pwd", "cd", "jobs");
    private static final List<Job> BACKGROUND_JOBS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            renderBackgroundJobs(false, null, false);
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

            String redirectTarget = parsedCommand.stdoutRedirectTarget;
            boolean appendStdout = parsedCommand.appendStdout;
            String stderrRedirectTarget = parsedCommand.stderrRedirectTarget;
            boolean appendStderr = parsedCommand.appendStderr;

            int pipeIndex = commandParts.indexOf("|");
            if (pipeIndex != -1) {
                List<List<String>> pipelineStages = splitPipelineStages(commandParts);

                if (pipelineStages.size() > 1) {
                    String pipelineOutput;

                    if (pipelineContainsBuiltin(pipelineStages)) {
                        File[] currentDirectoryHolder = new File[] { currentDirectory };
                        pipelineOutput = executePipelineWithBuiltins(pipelineStages, currentDirectoryHolder);
                        currentDirectory = currentDirectoryHolder[0];
                    } else {
                        pipelineOutput = executeExternalPipeline(pipelineStages, currentDirectory);
                    }

                    writeOutput(pipelineOutput, redirectTarget, appendStdout);
                }

                continue;
            }

            String commandName = commandParts.get(0);
            boolean runInBackground = commandParts.size() > 0 && commandParts.get(commandParts.size() - 1).equals("&");
            if (runInBackground) {
                commandParts = new ArrayList<>(commandParts.subList(0, commandParts.size() - 1));
                if (commandParts.isEmpty()) {
                    continue;
                }
                commandName = commandParts.get(0);
            }

            prepareRedirectTarget(redirectTarget, appendStdout);
            prepareRedirectTarget(stderrRedirectTarget, appendStderr);

            if(commandName.equals("exit") && (commandParts.size() == 1 || commandParts.get(1).equals("0"))){
                break;
            }
            else if(commandName.equals("echo")){
                writeOutput(String.join(" ", commandParts.subList(1, commandParts.size())) + System.lineSeparator(), redirectTarget, appendStdout);
            }
            else if(commandName.equals("pwd")){
                writeOutput(currentDirectory.getAbsolutePath() + System.lineSeparator(), redirectTarget, appendStdout);
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
                    writeError("cd: " + directory + ": No such file or directory" + System.lineSeparator(), stderrRedirectTarget, appendStderr);
                }
            }
            else if(commandName.equals("type") && commandParts.size() > 1){
                String sub = commandParts.get(1);
                if(BUILTINS.contains(sub)){
                    writeOutput(sub + " is a shell builtin" + System.lineSeparator(), redirectTarget, appendStdout);
                }
                else {
                    File executable = findExecutable(sub);
                    if (executable != null) {
                        writeOutput(sub + " is " + executable.getAbsolutePath() + System.lineSeparator(), redirectTarget, appendStdout);
                    } else {
                        writeError(sub + ": not found" + System.lineSeparator(), stderrRedirectTarget, appendStderr);
                    }
                }
            }
            else if(commandName.equals("jobs")){
                renderBackgroundJobs(true, redirectTarget, appendStdout);
            }
            else {
                File executable = findExecutable(commandName);
                if (executable != null) {
                    ProcessBuilder processBuilder = new ProcessBuilder(commandParts)
                            .directory(currentDirectory);

                    if (runInBackground) {
                        processBuilder.inheritIO();
                    } else {
                        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

                        if (redirectTarget != null) {
                            if (appendStdout) {
                                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(redirectTarget)));
                            } else {
                                processBuilder.redirectOutput(new File(redirectTarget));
                            }
                        } else {
                            processBuilder.inheritIO();
                        }

                        if (stderrRedirectTarget != null) {
                            if (appendStderr) {
                                processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(new File(stderrRedirectTarget)));
                            } else {
                                processBuilder.redirectError(new File(stderrRedirectTarget));
                            }
                        }
                    }

                    Process process = processBuilder.start();
                    if (runInBackground) {
                        String backgroundCommand = String.join(" ", commandParts);
                        Job job = new Job(nextJobNumber(), process.pid(), backgroundCommand, process);
                        BACKGROUND_JOBS.add(job);
                        System.out.println("[" + job.jobNumber + "] " + job.pid);
                    } else {
                        process.waitFor();
                    }
                } else {
                    writeError(cmd + ": command not found" + System.lineSeparator(), stderrRedirectTarget, appendStderr);
                }
            }
        }
    }

    private static void prepareRedirectTarget(String redirectTarget, boolean append) throws Exception {
        if (redirectTarget != null && !append) {
            Files.writeString(Path.of(redirectTarget), "");
        }
    }

    private static void writeOutput(String output, String redirectTarget, boolean append) throws Exception {
        if (redirectTarget != null) {
            if (append) {
                Files.writeString(Path.of(redirectTarget), output, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(Path.of(redirectTarget), output);
            }
        } else {
            System.out.print(output);
        }
    }

    private static void writeError(String output, String redirectTarget, boolean append) throws Exception {
        if (redirectTarget != null) {
            if (append) {
                Files.writeString(Path.of(redirectTarget), output, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(Path.of(redirectTarget), output);
            }
        } else {
            System.err.print(output);
        }
    }

    private static void renderBackgroundJobs(boolean includeRunningJobs, String redirectTarget, boolean append) throws Exception {
        if (BACKGROUND_JOBS.isEmpty()) {
            return;
        }

        List<Job> completedJobs = new ArrayList<>();
        int jobCount = BACKGROUND_JOBS.size();

        for (int i = 0; i < jobCount; i++) {
            Job job = BACKGROUND_JOBS.get(i);
            String marker = i == jobCount - 1 ? "+" : i == jobCount - 2 ? "-" : " ";
            boolean running = job.process.isAlive();

            if (running) {
                if (includeRunningJobs) {
                    writeOutput(job.format("Running", marker, true) + System.lineSeparator(), redirectTarget, append);
                }
            } else {
                writeOutput(job.format("Done", marker, false) + System.lineSeparator(), redirectTarget, append);
                completedJobs.add(job);
            }
        }

        if (!completedJobs.isEmpty()) {
            BACKGROUND_JOBS.removeAll(completedJobs);
        }
    }

    private static List<List<String>> splitPipelineStages(List<String> commandParts) {
        List<List<String>> stages = new ArrayList<>();
        List<String> currentStage = new ArrayList<>();

        for (String part : commandParts) {
            if (part.equals("|")) {
                if (!currentStage.isEmpty()) {
                    stages.add(currentStage);
                    currentStage = new ArrayList<>();
                }
            } else {
                currentStage.add(part);
            }
        }

        if (!currentStage.isEmpty()) {
            stages.add(currentStage);
        }

        return stages;
    }

    private static boolean pipelineContainsBuiltin(List<List<String>> stages) {
        for (List<String> stage : stages) {
            if (!stage.isEmpty() && BUILTINS.contains(stage.get(0))) {
                return true;
            }
        }

        return false;
    }

    private static String executePipelineWithBuiltins(List<List<String>> stages, File[] currentDirectoryHolder) throws Exception {
        String currentInput = "";

        for (List<String> stage : stages) {
            if (stage.isEmpty()) {
                continue;
            }

            if (BUILTINS.contains(stage.get(0))) {
                currentInput = executeBuiltinStage(stage, currentDirectoryHolder, currentInput);
            } else {
                currentInput = executeExternalStage(stage, currentDirectoryHolder[0], currentInput);
            }
        }

        return currentInput;
    }

    private static String executeBuiltinStage(List<String> commandParts, File[] currentDirectoryHolder, String input) throws Exception {
        String commandName = commandParts.get(0);

        if (commandName.equals("echo")) {
            return String.join(" ", commandParts.subList(1, commandParts.size())) + System.lineSeparator();
        }

        if (commandName.equals("pwd")) {
            return currentDirectoryHolder[0].getAbsolutePath() + System.lineSeparator();
        }

        if (commandName.equals("type") && commandParts.size() > 1) {
            String sub = commandParts.get(1);
            if (BUILTINS.contains(sub)) {
                return sub + " is a shell builtin" + System.lineSeparator();
            }

            File executable = findExecutable(sub);
            if (executable != null) {
                return sub + " is " + executable.getAbsolutePath() + System.lineSeparator();
            }

            writeError(sub + ": not found" + System.lineSeparator(), null, false);
            return "";
        }

        if (commandName.equals("cd") && commandParts.size() > 1) {
            String directory = commandParts.get(1);
            String targetDirectory = directory.equals("~") ? System.getenv("HOME") : directory;
            File newDirectory = targetDirectory == null ? null : new File(targetDirectory);
            if (newDirectory != null && !newDirectory.isAbsolute()) {
                newDirectory = new File(currentDirectoryHolder[0], directory);
            }

            if (newDirectory != null && newDirectory.isDirectory()) {
                currentDirectoryHolder[0] = newDirectory.getCanonicalFile();
            } else {
                writeError("cd: " + directory + ": No such file or directory" + System.lineSeparator(), null, false);
            }

            return "";
        }

        if (commandName.equals("jobs")) {
            StringBuilder output = new StringBuilder();
            List<String> lines = buildJobLines(true);
            for (String line : lines) {
                output.append(line).append(System.lineSeparator());
            }
            return output.toString();
        }

        if (commandName.equals("exit") && (commandParts.size() == 1 || commandParts.get(1).equals("0"))) {
            return "";
        }

        return input;
    }

    private static String executeExternalStage(List<String> commandParts, File currentDirectory, String input) throws Exception {
        File executable = findExecutable(commandParts.get(0));
        if (executable == null) {
            writeError(commandParts.get(0) + ": command not found" + System.lineSeparator(), null, false);
            return "";
        }

        Process process = new ProcessBuilder(commandParts)
                .directory(currentDirectory)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start();

        try (java.io.OutputStream processInput = process.getOutputStream()) {
            processInput.write(input.getBytes());
        }

        byte[] outputBytes = process.getInputStream().readAllBytes();
        process.waitFor();
        return new String(outputBytes);
    }

    private static String executeExternalPipeline(List<List<String>> stages, File currentDirectory) throws Exception {
        List<ProcessBuilder> processBuilders = new ArrayList<>();

        for (int i = 0; i < stages.size(); i++) {
            List<String> stage = stages.get(i);
            File executable = findExecutable(stage.get(0));
            if (executable == null) {
                writeError(stage.get(0) + ": command not found" + System.lineSeparator(), null, false);
                return "";
            }

            ProcessBuilder processBuilder = new ProcessBuilder(stage)
                    .directory(currentDirectory)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE);

            if (i > 0) {
                processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
            }

            processBuilders.add(processBuilder);
        }

        List<Process> processes = ProcessBuilder.startPipeline(processBuilders);
        Process lastProcess = processes.get(processes.size() - 1);
        byte[] outputBytes = lastProcess.getInputStream().readAllBytes();

        for (Process process : processes) {
            process.waitFor();
        }

        return new String(outputBytes);
    }

    private static List<String> buildJobLines(boolean includeRunningJobs) {
        List<String> lines = new ArrayList<>();
        if (BACKGROUND_JOBS.isEmpty()) {
            return lines;
        }

        List<Job> completedJobs = new ArrayList<>();
        int jobCount = BACKGROUND_JOBS.size();

        for (int i = 0; i < jobCount; i++) {
            Job job = BACKGROUND_JOBS.get(i);
            String marker = i == jobCount - 1 ? "+" : i == jobCount - 2 ? "-" : " ";
            boolean running = job.process.isAlive();

            if (running) {
                if (includeRunningJobs) {
                    lines.add(job.format("Running", marker, true));
                }
            } else {
                lines.add(job.format("Done", marker, false));
                completedJobs.add(job);
            }
        }

        if (!completedJobs.isEmpty()) {
            BACKGROUND_JOBS.removeAll(completedJobs);
        }

        return lines;
    }

    private static int nextJobNumber() {
        if (BACKGROUND_JOBS.isEmpty()) {
            return 1;
        }

        int highestJobNumber = 0;
        for (Job job : BACKGROUND_JOBS) {
            if (job.jobNumber > highestJobNumber) {
                highestJobNumber = job.jobNumber;
            }
        }

        return highestJobNumber + 1;
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
            } else if (currentChar == '|' && !inSingleQuotes && !inDoubleQuotes) {
                if (hasCurrentArg) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                    hasCurrentArg = false;
                }
                args.add("|");
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
        boolean appendStdout = false;
        String stderrRedirectTarget = null;
        boolean appendStderr = false;
        List<String> filteredArgs = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("&") && i == args.size() - 1) {
                filteredArgs.add(arg);
            } else if ((arg.equals(">") || arg.equals("1>") || arg.equals(">>") || arg.equals("1>>")) && i + 1 < args.size()) {
                redirectTarget = args.get(i + 1);
                appendStdout = arg.contains(">>");
                i++;
            } else if ((arg.equals("2>") || arg.equals("2>>")) && i + 1 < args.size()) {
                stderrRedirectTarget = args.get(i + 1);
                appendStderr = arg.equals("2>>");
                i++;
            } else {
                filteredArgs.add(arg);
            }
        }

        return new ParsedCommand(filteredArgs, redirectTarget, appendStdout, stderrRedirectTarget, appendStderr);
    }

    private static class ParsedCommand {
        private final List<String> arguments;
        private final String stdoutRedirectTarget;
        private final boolean appendStdout;
        private final String stderrRedirectTarget;
        private final boolean appendStderr;

        private ParsedCommand(List<String> arguments, String stdoutRedirectTarget, boolean appendStdout, String stderrRedirectTarget, boolean appendStderr) {
            this.arguments = arguments;
            this.stdoutRedirectTarget = stdoutRedirectTarget;
            this.appendStdout = appendStdout;
            this.stderrRedirectTarget = stderrRedirectTarget;
            this.appendStderr = appendStderr;
        }
    }

    private static class Job {
        private final int jobNumber;
        private final long pid;
        private final String command;
        private final Process process;

        private Job(int jobNumber, long pid, String command, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }

        private String format(String status, String marker, boolean running) {
            return "[" + jobNumber + "]" + marker + "  " + String.format("%-24s", status) + command + (running ? " &" : "");
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
