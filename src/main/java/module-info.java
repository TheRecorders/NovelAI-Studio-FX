module com.zxzinn.novelai {
    requires javafx.controls;
    requires javafx.fxml;
    requires static lombok;
    requires okhttp3;
    requires com.google.gson;
    requires java.desktop;
    requires org.apache.logging.log4j;
    requires org.yaml.snakeyaml;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.ikonli.core;
    requires org.apache.commons.io;
    requires javafx.web;
    requires jdk.jsobject;
    requires org.controlsfx.controls;
    requires java.logging;
    requires org.jsoup;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires annotations;
    requires com.google.guice;
    requires javafx.media;
    requires org.apache.commons.text;
    requires maven.model;
    requires java.net.http;
    requires org.kohsuke.github.api;
    requires org.apache.commons.codec;
    requires org.semver4j;
    requires org.apache.commons.lang3;

    opens com.zxzinn.novelai to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.api to com.google.gson, com.google.guice;
    opens com.zxzinn.novelai.controller.filemanager to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.utils.common to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.component to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.service.filemanager to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.utils.tokenizer to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.service.generation to javafx.fxml, com.google.guice;
    opens com.zxzinn.novelai.service to com.google.guice;
    opens com.zxzinn.novelai.service.ui to com.google.guice;
    opens com.zxzinn.novelai.utils.image to com.google.guice;
    opens com.zxzinn.novelai.utils.embed to com.google.guice;
    opens com.zxzinn.novelai.controller to javafx.fxml;

    exports com.zxzinn.novelai;
    exports com.zxzinn.novelai.api;
    exports com.zxzinn.novelai.controller.filemanager;
    exports com.zxzinn.novelai.controller;
    exports com.zxzinn.novelai.utils.common;
    exports com.zxzinn.novelai.component;
    exports com.zxzinn.novelai.service.filemanager;
    exports com.zxzinn.novelai.utils.tokenizer;
    exports com.zxzinn.novelai.service.generation;
    exports com.zxzinn.novelai.service.ui;
    exports com.zxzinn.novelai.utils.image;
    exports com.zxzinn.novelai.utils.embed;
    exports com.zxzinn.novelai.service;
    exports com.zxzinn.novelai.model;
    exports com.zxzinn.novelai.view;
    exports com.zxzinn.novelai.viewmodel;
    exports com.zxzinn.novelai.workflow;
    opens com.zxzinn.novelai.model to com.google.gson, com.google.guice, javafx.base;
    exports com.zxzinn.novelai.test to javafx.graphics;
    opens com.zxzinn.novelai.view to com.google.guice, javafx.fxml;
}