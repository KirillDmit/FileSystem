package ru.doczilla;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class FileDependencyResolver {

    private static final Pattern REQUIRE_PATTERN = Pattern.compile("require '(.+?)'");

    public static void main(String[] args) {
//        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        Path rootDir = Paths.get("src/main/resources");
        try {
            Map<String, List<String>> dependencies = new HashMap<>();
            Map<String, String> fileContents = new HashMap<>();

            // Сканирование файловой системы
            scanDirectory(rootDir, rootDir, dependencies, fileContents);

            // Проверка циклических зависимостей и топологическая сортировка
            List<String> sortedFiles = resolveDependencies(dependencies);

            // Склеивание файлов
            concatenateFiles(sortedFiles, fileContents, rootDir);

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static void scanDirectory(Path rootDir, Path currentDir,
                                      Map<String, List<String>> dependencies,
                                      Map<String, String> fileContents) throws IOException {
        Files.walk(currentDir).forEach(filePath -> {
            if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".txt")) {
                String relativePath = rootDir.relativize(filePath).toString().replace("\\", "/");
                try {
                    String content = Files.readString(filePath);
                    fileContents.put(relativePath, content);
                    dependencies.put(relativePath, extractDependencies(content));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private static List<String> extractDependencies(String content) {
        List<String> result = new ArrayList<>();
        Matcher matcher = REQUIRE_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static List<String> resolveDependencies(Map<String, List<String>> dependencies) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        // Initialize indegree and adjacency list
        for (String file : dependencies.keySet()) {
            indegree.put(file, 0);
            adjList.put(file, new ArrayList<>());
        }

        // Build graph
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            String file = entry.getKey();
            for (String dep : entry.getValue()) {
                adjList.putIfAbsent(dep, new ArrayList<>());
                indegree.putIfAbsent(dep, 0);
                adjList.get(dep).add(file);
                indegree.put(file, indegree.get(file) + 1);
            }
        }

        // Topological sort
        Queue<String> queue = new LinkedList<>();
        for (String file : indegree.keySet()) {
            if (indegree.get(file) == 0) {
                queue.add(file);
            }
        }

        List<String> sortedFiles = new ArrayList<>();
        while (!queue.isEmpty()) {
            String file = queue.poll();
            sortedFiles.add(file);
            for (String dependent : adjList.get(file)) {
                indegree.put(dependent, indegree.get(dependent) - 1);
                if (indegree.get(dependent) == 0) {
                    queue.add(dependent);
                }
            }
        }

        // Check for cycles
        if (sortedFiles.size() != dependencies.size()) {
            throw new IllegalStateException("Циклическая зависимость обнаружена!");
        }

        return sortedFiles;
    }

    private static void concatenateFiles(List<String> sortedFiles, Map<String, String> fileContents, Path rootDir) {
        Path outputFile = rootDir.resolve("result.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (String file : sortedFiles) {
                writer.write(fileContents.get(file));
                writer.newLine();
            }
            System.out.println("Файлы успешно склеены в: " + outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
