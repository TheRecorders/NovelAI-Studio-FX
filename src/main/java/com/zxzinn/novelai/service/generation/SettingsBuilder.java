package com.zxzinn.novelai.service.generation;

import com.zxzinn.novelai.component.PromptArea;
import javafx.scene.control.*;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

@Builder
public class SettingsBuilder {
    private TextField apiKeyField;
    private ComboBox<String> modelComboBox;
    private TextField widthField;
    private TextField heightField;
    private ComboBox<String> samplerComboBox;
    private TextField stepsField;
    private TextField seedField;
    private ComboBox<String> generateCountComboBox;
    private PromptArea positivePromptArea;
    private PromptArea negativePromptArea;
    private TextField outputDirectoryField;
    private ComboBox<String> generationModeComboBox;
    private CheckBox smeaCheckBox;
    private CheckBox smeaDynCheckBox;
    private Slider strengthSlider;
    private TextField extraNoiseSeedField;
    private TextField ratioField;
    private TextField countField;

    public void loadSettings(@NotNull GenerationSettingsManager settingsManager) {
        settingsManager.loadSettings(
                apiKeyField, modelComboBox, widthField, heightField, samplerComboBox,
                stepsField, seedField, generateCountComboBox, positivePromptArea, negativePromptArea,
                outputDirectoryField, generationModeComboBox, smeaCheckBox, smeaDynCheckBox,
                strengthSlider, extraNoiseSeedField, ratioField, countField
        );
    }
}