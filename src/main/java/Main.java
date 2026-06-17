import java.util.*;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String cmd = sc.nextLine();

            if(cmd.equals("exit") || cmd.equals("exit 0")){
                break;
            }
            else if(cmd.startsWith("echo ")){
                System.out.println(cmd.substring(5));
            }
            else if(cmd.startsWith("type")){
                String sub = cmd.substring(5);
                if(sub.equals("exit") || sub.equals("echo") || sub.equals("type")){
                    System.out.println(sub + " is a shell builtin");
                }
                else {
                    // Search for the command in PATH
                    String[] pathDirs = System.getenv("PATH").split(File.pathSeparator);
                    boolean found = false;
                    for (String dir : pathDirs) {
                        File file = new File(dir, sub);
                        if (file.exists() && file.canExecute()) {
                            System.out.println(sub + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        System.out.println(sub + ": not found");
                    }
                }
            }
            else System.out.println(cmd + ": command not found");
        }
    }
}
