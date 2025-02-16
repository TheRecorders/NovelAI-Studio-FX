package com.zxzinn.novelai.service.filemanager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zxzinn.novelai.utils.common.PropertiesManager;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

@Log4j2
@Singleton
public class FileManagerService {
    private static final String WATCHED_DIRECTORIES_KEY = "watchedDirectories";
    private static final int BATCH_SIZE = 100;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private final Set<Path> watchedDirectories;
    private final Map<WatchKey, Path> watchKeyToPath;
    private final WatchService watchService;
    private final PropertiesManager propertiesManager;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    @Setter private BiConsumer<String, WatchEvent.Kind<?>> fileChangeListener;

    @Inject
    public FileManagerService(PropertiesManager propertiesManager) throws IOException {
        this.watchedDirectories = ConcurrentHashMap.newKeySet();
        this.watchKeyToPath = new ConcurrentHashMap<>();
        this.watchService = FileSystems.getDefault().newWatchService();
        this.propertiesManager = propertiesManager;
        this.executorService = new ThreadPoolExecutor(
                THREAD_POOL_SIZE, THREAD_POOL_SIZE,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        loadWatchedDirectories();
        startWatchService();
    }

    private void loadWatchedDirectories() {
        List<String> savedDirectories = propertiesManager.getStringList(WATCHED_DIRECTORIES_KEY, new ArrayList<>());
        for (String dir : savedDirectories) {
            try {
                addWatchedDirectory(dir);
            } catch (IOException e) {
                log.error("無法加載已保存的監視目錄: {}", dir, e);
            }
        }
    }

    private void startWatchService() {
        scheduledExecutorService.scheduleWithFixedDelay(this::processWatchEvents, 0, 1, TimeUnit.SECONDS);
    }

    private void processWatchEvents() {
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            Path dir = watchKeyToPath.get(key);
            if (dir == null) {
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path fileName = ev.context();
                Path fullPath = dir.resolve(fileName);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(fullPath)) {
                        try {
                            addWatchedDirectory(fullPath.toString());
                            refreshDirectory(dir); // 刷新父目錄
                        } catch (IOException e) {
                            log.error("無法添加新創建的目錄到監視列表: {}", fullPath, e);
                        }
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    if (watchedDirectories.remove(fullPath)) {
                        removeWatchKey(fullPath);
                        refreshDirectory(dir); // 刷新父目錄
                    }
                }

                final Path finalFullPath = fullPath;
                final WatchEvent.Kind<?> finalKind = kind;
                Platform.runLater(() -> {
                    if (fileChangeListener != null) {
                        fileChangeListener.accept(finalFullPath.toString(), finalKind);
                    }
                });
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyToPath.remove(key);
                if (watchKeyToPath.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void addWatchedDirectory(String path) throws IOException {
        Path directory = Paths.get(path);
        if (Files.isDirectory(directory) && !watchedDirectories.contains(directory)) {
            WatchKey key = directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            watchedDirectories.add(directory);
            watchKeyToPath.put(key, directory);
            log.info("已添加監視目錄: {}", directory);

            propertiesManager.addToStringList(WATCHED_DIRECTORIES_KEY, path);

            if (fileChangeListener != null) {
                fileChangeListener.accept(path, StandardWatchEventKinds.ENTRY_CREATE);
            }
        }
    }

    public void removeWatchedDirectory(String path) {
        Path directory = Paths.get(path).toAbsolutePath().normalize();
        log.info("嘗試移除監視目錄: {}", directory);

        Optional<Path> watchedDir = watchedDirectories.stream()
                .filter(dir -> dir.endsWith(directory.getFileName()))
                .findFirst();

        if (watchedDir.isPresent()) {
            Path dirToRemove = watchedDir.get();
            watchedDirectories.remove(dirToRemove);
            removeWatchKey(dirToRemove);
            log.info("已移除監視目錄: {}", dirToRemove);
            propertiesManager.removeFromStringList(WATCHED_DIRECTORIES_KEY, dirToRemove.toString());
        } else {
            log.warn("嘗試移除不存在的監視目錄: {}", directory);
        }
    }

    private void removeWatchKey(Path directory) {
        WatchKey keyToRemove = null;
        for (Map.Entry<WatchKey, Path> entry : watchKeyToPath.entrySet()) {
            if (entry.getValue().equals(directory)) {
                keyToRemove = entry.getKey();
                break;
            }
        }
        if (keyToRemove != null) {
            keyToRemove.cancel();
            Path removedPath = watchKeyToPath.remove(keyToRemove);
            if (removedPath != null) {
                log.info("已從 watchKeyToPath 中移除路徑: {}", removedPath);
            } else {
                log.warn("無法從 watchKeyToPath 中移除路徑: {}", directory);
            }
        } else {
            log.warn("未找到與路徑 {} 對應的 WatchKey", directory);
        }
    }

    public CompletableFuture<TreeItem<String>> getDirectoryTree() {
        return CompletableFuture.supplyAsync(() -> {
            TreeItem<String> root = new TreeItem<>("監視的目錄");
            root.setExpanded(true);

            List<CompletableFuture<TreeItem<String>>> futures = watchedDirectories.stream()
                    .map(this::createTreeItemAsync)
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<TreeItem<String>> future : futures) {
                root.getChildren().add(future.join());
            }

            return root;
        }, executorService);
    }

    @NotNull
    @Contract("_ -> new")
    private CompletableFuture<TreeItem<String>> createTreeItemAsync(Path path) {
        return CompletableFuture.supplyAsync(() -> createTreeItem(path.toFile()), executorService);
    }

    @NotNull
    private TreeItem<String> createTreeItem(@NotNull File file) {
        TreeItem<String> item = new TreeItem<>(file.getName(), getFileIcon(file));
        if (file.isDirectory()) {
            CompletableFuture.runAsync(() -> loadChildrenInBatches(item, file), executorService);
        }
        return item;
    }

    private void loadChildrenInBatches(TreeItem<String> parentItem, @NotNull File parentFile) {
        Collection<File> children = FileUtils.listFiles(parentFile, null, false);
        List<File> childList = new ArrayList<>(children);

        for (int i = 0; i < childList.size(); i += BATCH_SIZE) {
            final int start = i;
            final int end = Math.min(start + BATCH_SIZE, childList.size());
            CompletableFuture.runAsync(() -> {
                List<TreeItem<String>> batch = childList.subList(start, end).stream()
                        .map(this::createTreeItem)
                        .toList();
                Platform.runLater(() -> parentItem.getChildren().addAll(batch));
            }, executorService);
        }
    }

    @NotNull
    private FontIcon getFileIcon(@NotNull File file) {
        if (file.isDirectory()) {
            return new FontIcon(FontAwesomeSolid.FOLDER);
        } else {
            String extension = FilenameUtils.getExtension(file.getName());
            return switch (extension.toLowerCase()) {
                case "png", "jpg", "jpeg", "gif" -> new FontIcon(FontAwesomeSolid.IMAGE);
                case "txt" -> new FontIcon(FontAwesomeSolid.FILE_ALT);
                default -> new FontIcon(FontAwesomeSolid.FILE);
            };
        }
    }

    public String getWatchedDirectoryFullPath(String dirName) {
        for (Path path : watchedDirectories) {
            if (path.getFileName().toString().equals(dirName)) {
                return path.toString();
            }
        }
        return null;
    }

    public File chooseDirectory(Window ownerWindow) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("選擇要監視的目錄");
        return directoryChooser.showDialog(ownerWindow);
    }

    public void refreshDirectory(Path directory) {
        WatchKey key = null;
        for (Map.Entry<WatchKey, Path> entry : watchKeyToPath.entrySet()) {
            if (entry.getValue().equals(directory)) {
                key = entry.getKey();
                break;
            }
        }
        if (key != null) {
            key.pollEvents();
            Path dir = watchKeyToPath.get(key);
            if (dir != null) {
                final Path finalDir = dir;
                Platform.runLater(() -> {
                    if (fileChangeListener != null) {
                        fileChangeListener.accept(finalDir.toString(), StandardWatchEventKinds.ENTRY_MODIFY);
                    }
                });
            }
        }
    }

    public void shutdown() {
        executorService.shutdown();
        scheduledExecutorService.shutdown();
        try {
            watchService.close();
        } catch (IOException e) {
            log.error("關閉 WatchService 時發生錯誤", e);
        }
    }
}