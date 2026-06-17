import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String cmd = sc.nextLine();

            if(cmd.equals("exit")){
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
                else System.out.println(sub + " is not a shell builtin");
            }
            else System.out.println(cmd + ": command not found");
        }
    }
}
