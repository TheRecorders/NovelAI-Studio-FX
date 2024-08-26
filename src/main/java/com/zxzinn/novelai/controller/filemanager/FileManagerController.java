package com.zxzinn.novelai.controller.filemanager;

import com.zxzinn.novelai.component.PreviewPane;
import com.zxzinn.novelai.service.filemanager.*;
import com.zxzinn.novelai.service.ui.AlertService;
import com.zxzinn.novelai.utils.common.TxtProcessor;
import com.zxzinn.novelai.utils.image.ImageUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Log4j2
public class FileManagerController {
    @FXML private TreeView<String> fileTreeView;
    @FXML private Button selectAllButton;
    @FXML protected StackPane previewContainer;
    @FXML private TextArea metadataTextArea;
    @FXML private TextField searchField;
    @FXML private Button addButton;
    @FXML private Button removeButton;
    @FXML private Button clearMetadataButton;
    @FXML private Button mergeTxtButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    private final FileManagerService fileManagerService;
    private final FilePreviewService filePreviewService;
    private final MetadataService metadataService;
    private final AlertService alertService;
    private final FileTreeController fileTreeController;
    protected PreviewPane previewPane;
    private final ExecutorService executorService;

    public FileManagerController(FileManagerService fileManagerService,
                                 FilePreviewService filePreviewService,
                                 MetadataService metadataService,
                                 AlertService alertService) {
        this.fileManagerService = fileManagerService;
        this.filePreviewService = filePreviewService;
        this.metadataService = metadataService;
        this.alertService = alertService;
        this.fileTreeController = new FileTreeController(fileManagerService);
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @FXML
    public void initialize() {
        previewPane = new PreviewPane(filePreviewService);
        previewContainer.getChildren().add(previewPane);
        setupEventHandlers();
        fileTreeController.setFileTreeView(fileTreeView);
        fileTreeController.refreshTreeView();

        fileManagerService.setFileChangeListener(this::handleFileChange);

        fileTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        fileTreeView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.CONTROL) {
                fileTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            }
        });
    }

    private void handleFileChange(String path, WatchEvent.Kind<?> kind) {
        fileTreeController.updateTreeItem(path, kind);
        updatePreview(fileTreeView.getSelectionModel().getSelectedItem());
    }

    private void setupEventHandlers() {
        addButton.setOnAction(event -> addWatchedDirectory());
        removeButton.setOnAction(event -> removeWatchedDirectory());

        fileTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                updatePreview(newValue);
            }
        });

        fileTreeView.addEventHandler(TreeItem.branchExpandedEvent(), fileTreeController::handleBranchExpanded);
        fileTreeView.addEventHandler(TreeItem.branchCollapsedEvent(), fileTreeController::handleBranchCollapsed);

        searchField.textProperty().addListener((observable, oldValue, newValue) ->
                fileTreeController.setSearchFilter(newValue));

        selectAllButton.setOnAction(event -> selectAllInSelectedDirectory());
        clearMetadataButton.setOnAction(event -> clearMetadataForSelectedFiles());
        mergeTxtButton.setOnAction(event -> mergeSelectedTxtFiles());
    }

    private void mergeSelectedTxtFiles() {
        List<File> selectedFiles = getSelectedTxtFiles();
        if (selectedFiles.isEmpty()) {
            alertService.showAlert("警告", "請選擇要合併的txt文件。");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("選擇輸出文件");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT Files", "*.txt"));
        File outputFile = fileChooser.showSaveDialog(fileTreeView.getScene().getWindow());

        if (outputFile != null) {
            try {
                TxtProcessor.mergeAndProcessTxtFiles(selectedFiles, outputFile);
                alertService.showAlert("成功", "txt文件已成功合併和處理。");
            } catch (IOException e) {
                log.error("合併txt文件時發生錯誤", e);
                alertService.showAlert("錯誤", "合併txt文件時發生錯誤: " + e.getMessage());
            }
        }
    }

    private List<File> getSelectedTxtFiles() {
        return fileTreeView.getSelectionModel().getSelectedItems().stream()
                .map(item -> new File(fileTreeController.buildFullPath(item)))
                .filter(this::isTxtFile)
                .collect(Collectors.toList());
    }

    private boolean isTxtFile(@NotNull File file) {
        if (!file.isFile()) return false;
        return file.getName().toLowerCase().endsWith(".txt");
    }

    private void clearMetadataForSelectedFiles() {
        List<File> selectedFiles = getSelectedImageFiles();
        if (selectedFiles.isEmpty()) {
            alertService.showAlert("警告", "請選擇要清除元數據的 PNG 文件。");
            return;
        }

        AtomicInteger processedCount = new AtomicInteger(0);
        int totalFiles = selectedFiles.size();

        for (File file : selectedFiles) {
            if (!file.getName().toLowerCase().endsWith(".png")) {
                continue;
            }
            executorService.submit(() -> {
                try {
                    processFile(file);
                    int completed = processedCount.incrementAndGet();
                    updateProgressOnUI(completed, totalFiles);
                    if (completed == totalFiles) {
                        onProcessingComplete(selectedFiles);
                    }
                } catch (Exception e) {
                    log.error("處理文件時發生錯誤: {}", file.getName(), e);
                    Platform.runLater(() -> alertService.showAlert("錯誤", "處理文件時發生錯誤: " + e.getMessage()));
                }
            });
        }
    }

    private void processFile(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("無法讀取圖像文件: " + file.getName());
        }

        ImageUtils.clearMetadata(image);

        File cleanedDir = new File(file.getParentFile(), "cleaned");
        if (!cleanedDir.exists() && !cleanedDir.mkdir()) {
            throw new IOException("無法創建 cleaned 目錄");
        }

        File outputFile = new File(cleanedDir, file.getName());
        ImageUtils.saveImage(image, outputFile);
    }

    private void updateProgressOnUI(int completed, int total) {
        Platform.runLater(() -> {
            double progress = (double) completed / total;
            progressBar.setProgress(progress);
            int percentage = (int) (progress * 100);
            progressLabel.setText(String.format("處理中... %d%%", percentage));
        });
    }

    private void onProcessingComplete(List<File> processedFiles) {
        Platform.runLater(() -> {
            alertService.showAlert("成功", String.format("已處理 %d 個文件的元數據清除。", processedFiles.size()));
            updatePreview(fileTreeView.getSelectionModel().getSelectedItem());
            refreshProcessedDirectories(processedFiles);
            fileTreeController.refreshTreeView();
        });
    }

    private void refreshProcessedDirectories(@NotNull List<File> processedFiles) {
        for (File file : processedFiles) {
            Path parentDir = file.getParentFile().toPath();
            Path cleanedDir = parentDir.resolve("cleaned");
            if (Files.exists(cleanedDir)) {
                try {
                    fileManagerService.addWatchedDirectory(cleanedDir.toString());
                } catch (IOException e) {
                    log.error("無法添加 cleaned 目錄到監視列表", e);
                }
            }
        }
    }

    private List<File> getSelectedImageFiles() {
        return fileTreeView.getSelectionModel().getSelectedItems().stream()
                .map(item -> new File(fileTreeController.buildFullPath(item)))
                .filter(this::isImageFile)
                .collect(Collectors.toList());
    }

    private void selectAllInSelectedDirectory() {
        TreeItem<String> selectedItem = fileTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            fileTreeController.selectAllInDirectory(selectedItem);
        }
    }

    private boolean isImageFile(@NotNull File file) {
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif");
    }

    private void addWatchedDirectory() {
        File selectedDirectory = fileManagerService.chooseDirectory(fileTreeView.getScene().getWindow());
        if (selectedDirectory != null) {
            try {
                fileManagerService.addWatchedDirectory(selectedDirectory.getAbsolutePath());
                fileTreeController.refreshTreeView();
            } catch (IOException e) {
                log.error("無法添加監視目錄", e);
                alertService.showAlert("錯誤", "無法添加監視目錄: " + e.getMessage());
            }
        }
    }

    private void removeWatchedDirectory() {
        TreeItem<String> selectedItem = fileTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            String path = fileTreeController.buildFullPath(selectedItem);
            if (path.isEmpty()) {
                alertService.showAlert("警告", "無法移除根目錄。請選擇一個具體的監視目錄。");
                return;
            }
            fileManagerService.removeWatchedDirectory(path);
            fileTreeController.refreshTreeView();
        } else {
            alertService.showAlert("警告", "請選擇一個要移除的監視目錄。");
        }
    }

    private void updatePreview(TreeItem<String> item) {
        String fullPath = fileTreeController.buildFullPath(item);
        File file = new File(fullPath);
        previewPane.updatePreview(file);
        updateMetadataList(file);
    }

    private void updateMetadataList(File file) {
        if (file != null && file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
            metadataTextArea.setText("正在讀取元數據...");

            CompletableFuture<String> futureMetadata = metadataService.getFormattedMetadataAsync(file);
            futureMetadata.thenAcceptAsync(metadata -> {
                Platform.runLater(() -> {
                    metadataTextArea.setText(metadata);
                });
            }, executorService).exceptionally(ex -> {
                Platform.runLater(() -> {
                    metadataTextArea.setText("讀取元數據時發生錯誤: " + ex.getMessage());
                });
                return null;
            });
        } else {
            metadataTextArea.clear();
            if (file != null && file.isFile() && !file.getName().toLowerCase().endsWith(".png")) {
                metadataTextArea.setText("不支持的文件類型：僅支持 PNG 文件");
            }
        }
    }

    public void shutdown() {
        fileManagerService.shutdown();
        executorService.shutdown();
        metadataService.shutdown();
    }
}