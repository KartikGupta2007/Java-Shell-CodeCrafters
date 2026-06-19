import java.util.*;
import java.io.File;

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

            String[] commandParts = cmd.split("\\s+");
            String commandName = commandParts[0];

            if(cmd.equals("exit") || cmd.equals("exit 0")){
                break;
            }
            else if(cmd.startsWith("echo ")){
                System.out.println(cmd.substring(5));
            }
            else if(cmd.equals("pwd")){
                System.out.println(currentDirectory.getAbsolutePath());
            }
            else if(cmd.startsWith("cd ")){
                String directory = cmd.substring(3);
                File newDirectory = new File(directory);
                if (!newDirectory.isAbsolute()) {
                    newDirectory = new File(currentDirectory, directory);
                }

                if (newDirectory.isDirectory()) {
                    currentDirectory = newDirectory.getCanonicalFile();
                } else {
                    System.out.println("cd: " + directory + ": No such file or directory");
                }
            }
            else if(cmd.startsWith("type")){
                String sub = cmd.substring(5);
                if(BUILTINS.contains(sub)){
                    System.out.println(sub + " is a shell builtin");
                }
                else {
                    File executable = findExecutable(sub);
                    if (executable != null) {
                        System.out.println(sub + " is " + executable.getAbsolutePath());
                    } else {
                        System.out.println(sub + ": not found");
                    }
                }
            }
            else {
                File executable = findExecutable(commandName);
                if (executable != null) {
                    Process process = new ProcessBuilder(commandParts)
                            .directory(currentDirectory)
                            .inheritIO()
                            .start();
                    process.waitFor();
                } else {
                    System.out.println(cmd + ": command not found");
                }
            }
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
