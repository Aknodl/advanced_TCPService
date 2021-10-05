import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.LongStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class Server {
    private final ServerSocket serverSocket;
    private static ExecutorService clientExecutorService;
    private static ExecutorService downloadExecutorService;
    private static ExecutorService fileReaderExecutorService;

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        clientExecutorService = new ThreadPoolExecutor(10, 100, 60, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(30));
        downloadExecutorService = Executors.newFixedThreadPool(20);
        fileReaderExecutorService = Executors.newSingleThreadExecutor();
    }

    public void run() {
        System.out.println("Server started");
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                clientExecutorService.submit(() -> handle(socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        try {
            Server server = new Server(8099);
            server.run();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private void handle(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            System.out.println("New client connected");
            String s = in.readLine();
            if (s == null) {
                out.println("not results");
                return;
            }
            JSONObject jsonObject = new JSONObject(s);
            Command command = Command.valueOf((String) jsonObject.get(JsonKey.COMMAND.name()));
            switch (command) {
                case DOWNLOAD:
                    downloadByUrls(jsonObject.get(JsonKey.ARGUMENT.name()), out);
                    break;
                case SUM:
                    calculateSum(jsonObject.get(JsonKey.ARGUMENT.name()), out);
                    break;
                case ZIP:
                    zipFromDirectory(jsonObject.get(JsonKey.ARGUMENT.name()), out);
                    break;
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void calculateSum(Object argument, PrintWriter out) {
        if (argument instanceof Number) {
            long arg = ((Number) argument).longValue();
            long sum = LongStream.range(0, arg).parallel().sum();
            out.println(sum);
        } else out.println("sum error");
    }

    private void downloadByUrls(Object arguments, PrintWriter out) throws URISyntaxException {
        if (arguments == null) {
            out.println("downloaded");
        }
        if (arguments instanceof JSONArray) {
            JSONArray args = (JSONArray) arguments;
            List<CompletableFuture<String>> completableFutures = new ArrayList<>();
            CompletableFuture<List<String>> result = new CompletableFuture<>();
            result.thenAccept(strings ->
                    out.println(strings.stream().noneMatch("error"::equals) ? "downloaded" : "download error"));
            args.forEach(arg ->
                    completableFutures.add(CompletableFuture.supplyAsync(() ->
                            download((String) arg), downloadExecutorService)));
            allOfFutures(completableFutures, result);
        } else out.println("download error");
    }

    private void zipFromDirectory(Object argument, PrintWriter out) {
        if (argument == null) {
            out.println("not directory");
        }
        if (argument instanceof String) {
            String arg = (String) argument;
            File folder = new File(arg);
            if (folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    try {
                        FileOutputStream fos = new FileOutputStream("dirCompressed.zip");
                        ZipOutputStream zipOutputStream = new ZipOutputStream(fos);
                        List<CompletableFuture<String>> completableFutures = new ArrayList<>();
                        CompletableFuture<List<String>> result = new CompletableFuture<>();
                        result.thenAccept(strings -> {
                            try {
                                zipOutputStream.close();
                                fos.close();
                                out.println(strings.stream().noneMatch("error"::equals) ? "archived" : "archived error");
                            } catch (Exception ignored) {
                            }
                        });
                        for (var file : files) {
                            if (file.isFile()) {
                                completableFutures.add(CompletableFuture.supplyAsync(() ->
                                        movedToArchive(file, zipOutputStream), fileReaderExecutorService));
                            }
                        }
                        allOfFutures(completableFutures, result);
                    } catch (IOException e) {
                        out.println("archived error");
                    }
                }
            } else out.println("folder is not directory");
        } else out.println("archived error");
    }

    private void allOfFutures(List<CompletableFuture<String>> completableFutures, CompletableFuture<List<String>> result) {
        for (CompletableFuture<?> future : completableFutures) {
            future.handle((__, ex) -> ex == null || result.completeExceptionally(ex));
        }
        allOf(completableFutures).thenAccept(result::complete);
    }

    private String movedToArchive(File file, ZipOutputStream zipOutputStream) {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zipOutputStream.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOutputStream.write(bytes, 0, length);
            }
            return "ok";
        } catch (IOException e) {
            return "error";
        }
    }

    private String download(String url) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .build();
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .join()
                    .body() != null ? "ok" : "error";
        } catch (Exception e) {
            return "error";
        }
    }

    public static <T> CompletableFuture<List<T>> allOf(
            Collection<CompletableFuture<T>> futures) {
        return futures.stream()
                .collect(collectingAndThen(
                        toList(),
                        l -> CompletableFuture.allOf(l.toArray(new CompletableFuture[0]))
                                .thenApply(__ -> l.stream()
                                        .map(CompletableFuture::join)
                                        .collect(toList()))));
    }
}
