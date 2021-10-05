import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class Client {

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    public void connect(String ip, int port) throws IOException {
        clientSocket = new Socket(ip, port);
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    public static void main(String[] args) {
        List<String> urls = List.of("https://www.baeldung.com/a-guide-to-java-sockets", "https://www.baeldung.com/java-9-http-client");
        //TODO add path
        String path = "";
        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    var client = new Client();
                    client.connect("127.0.0.1", 8099);
                    int random = new Random().nextInt(3) + 1;
                    if (random == 1) {
                        System.out.println(client.calculateSum(2000000000L));
                    } else if (random == 2) {
                        System.out.println(client.uploadContent(urls));
                    } else {
                        System.out.println(client.zipFromDirectory(path));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public String uploadContent(List<String> urls) throws IOException {
        JSONObject jsonObject = getJson(Command.DOWNLOAD, urls);
        out.println(jsonObject);
        String result;
        do {
            result = in.readLine();
        } while (result == null);
        return result;
    }

    public String calculateSum(long num) throws IOException {
        JSONObject jsonObject = getJson(Command.SUM, num);
        out.println(jsonObject);
        String result;
        do {
            result = in.readLine();
        } while (result == null);
        return result;
    }

    public String zipFromDirectory(String path) throws IOException {
        JSONObject jsonObject = getJson(Command.ZIP, path);
        out.println(jsonObject);
        return in.readLine();
    }

    private JSONObject getJson(Command command, Object arg) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JsonKey.COMMAND.name(), command);
        jsonObject.put(JsonKey.ARGUMENT.name(), arg);
        return jsonObject;
    }

}
