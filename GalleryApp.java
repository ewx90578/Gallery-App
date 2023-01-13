package cs1302.gallery;

import java.lang.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import javafx.concurrent.Task;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    /** List for choices in Dropbox. */
    private static final String[] CHOICES = new String[] {"movie", "podcast",
        "music", "musicVideo", "audiobook", "shortFilm",
        "tvShow", "software", "ebook", "all"};

    private Stage stage;
    private Scene scene;
    private HBox root;
    private VBox stuff;
    private Button playButton;
    private TextField searchBar;
    private ComboBox<String> options;
    private Button getImageButton;
    private Label instructionLabel;
    private ImageView results;
    private Image image;
    private ProgressBar searchProgress;
    private Label imagesProvided;
    private TilePane pictures;
    private ItunesResponse itunesResponse;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new HBox(5);
        this.stuff = new VBox();
        this.playButton = new Button("Play");
        this.searchBar = new TextField("Kim Seokjin");
        this.options = new ComboBox<>();
        this.getImageButton = new Button("Get Images");
        instructionLabel = new Label("Type in a term, select a media type, then click the button.");
        this.image = new Image("file:resources/default.png", 750, 600, false, true);
        this.results = new ImageView(image);
        this.searchProgress = new ProgressBar();
        this.imagesProvided = new Label(" Images provide by ITunes Search API");
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        HBox.setHgrow(root, Priority.ALWAYS);
        this.stuff.setMaxHeight(Double.MAX_VALUE);
        //Adds choices to options
        for (int i = 0; i < CHOICES.length; i++) {
            this.options.getItems().add(CHOICES[i]);
        }
        this.options.setValue(CHOICES[2]);
        playButton.setDisable(true);

        /** Search Button */
        getImageButton.setOnAction(e -> {
            searchProgress.setProgress(0);
            //Creates new task with Void return type to load page
            Task<Void> loadTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        loadPage();
                        return null;
                    }
                };
            runNow(loadTask);
        });

        //Makes it so changeImages only applies once every two seconds
        EventHandler<ActionEvent> change = event -> changeImages();
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(2), change);
        Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.getKeyFrames().add(keyFrame);

        /** Play/Pause Button*/
        playButton.setOnAction(e -> {
            if (this.playButton.getText() == "Play") {
                this.playButton.setText("Pause");
                //Plays timeline
                timeline.play();
            } else {
                this.playButton.setText("Play");
                timeline.pause();
            } //if
        }); //playButton

        HBox progressLine = new HBox();
        this.searchProgress.setMaxWidth(Double.MAX_VALUE);
        //Creates horizontal layer for search progress
        progressLine.getChildren().addAll(searchProgress, imagesProvided);
        //Creates horizontal layer for search bar
        this.root.getChildren().addAll(playButton, searchBar, options, getImageButton);
        //Adds everything from top to bottom to scene
        this.stuff.getChildren().addAll(root, instructionLabel, results, progressLine);
        this.stuff.setFillWidth(true);
        System.out.println("init() called");
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.scene = new Scene(this.stuff);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    /** {@inheritDoc} */
    @Override
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
    } // stop

    /**
     * Loads the beautiful gallery app.
     */
    private void loadPage() {
        Platform.runLater(() -> {
            this.getImageButton.setDisable(true);
            instructionLabel.setText("Getting images...");
            createLink(searchBar.getText());
            this.getImageButton.setDisable(false); //Avoids spamming
        });
    }

    /**
     * Creates link by using request to get a link to search iTunes API.
     * It then uses Json and Gson to parse results in to array in iTunesResponse.
     * @param searched is term used in search bar.
     */
    public void createLink(String searched) {
        try {
            searchProgress.setProgress(0);
            String term = URLEncoder.encode(searched, StandardCharsets.UTF_8);
            String media = URLEncoder.encode(options.getValue(), StandardCharsets.UTF_8);
            String limit = URLEncoder.encode("200", StandardCharsets.UTF_8);
            String query = String.format("?term=%s&media=%s&limit=%s", term, media, limit);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://itunes.apple.com/search" + query))
                .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
            //Throws exception if there is page is not found
            if (response.statusCode() != 200) {
                throw new IOException(response.toString());
            } // if
            //Get request body
            String jsonString = response.body();
            /** Creates new response and allocates images into arrays */
            this.itunesResponse = GSON.fromJson(jsonString, cs1302.gallery.ItunesResponse.class);
            this.itunesResponse.resultCount = itunesResponse.results.length;

            System.out.println("Num Results " + itunesResponse.resultCount);
            if (itunesResponse.resultCount < 21) {
                throw new IOException("Cannot find more than 21 images");
            } //Doesn't continue if response doesn't have 21 or more
            //Creates new images in tilePane
            createImages(itunesResponse);
            this.playButton.setDisable(false);
            //Binds progress bar to createTask[createImages]
            this.instructionLabel.setText(request.toString());
        } catch (IOException | InterruptedException e) {
            this.instructionLabel.setText("Last attempt to get images failed...");
            alertError(e);
        }
    } //createLink

     /**
     * Show a modal error alert based on {@code cause}.
     * @param cause a {@link java.lang.Throwable Throwable} that caused the alert
     */
    public static void alertError(Throwable cause) {
        TextArea text = new TextArea(cause.toString());
        text.setEditable(false);
        Alert alert = new Alert(AlertType.ERROR);
        alert.getDialogPane().setContent(text);
        alert.setResizable(true);
        alert.showAndWait();
    } // alertError

    /**
     * Creates tilepane with 5 rows of 5 photos each.
     * @param response Itunes search results
     */
    private void createImages(ItunesResponse response) {
        pictures = new TilePane();
        int counter = 0;
        pictures.setPrefRows(5);
        pictures.setPrefColumns(4);

        //Loops through entire tilepane
        for (int i = 0; i < pictures.getPrefRows() * pictures.getPrefColumns(); i++) {
            ItunesResult result = response.results[i];
            Image cover = new Image(result.artworkUrl100, 150, 150, true, true);
            Platform.runLater(() -> {
                pictures.getChildren().add(new ImageView(cover)); //Adds imageview to tile
            });
            this.searchProgress.setProgress((double)(i + 1) / 20);
        } //for
        //Gets rid of default ImageView and replaces it with pictures
        Platform.runLater(() -> {
            this.stuff.getChildren().remove(2);
            this.stuff.getChildren().add(2, pictures);
        });
    } //createImages

    /**
     * Prints out JSON line for itunes response.
     * @param response Itunes search results
     */
    public static void printItunesResponse(ItunesResponse response) {
        System.out.println(GSON.toJson(response));
        for (int i = 0; i < response.results.length; i++) {
            System.out.printf("response.results[%d]:\n", i);
            ItunesResult result = response.results[i];
            System.out.printf(" - wrapperType = %s\n", result.wrapperType);
            System.out.printf(" - kind = %s\n", result.kind);
            System.out.printf(" - artworkUrl100 = %s\n", result.artworkUrl100);
        }
    }

    /**
     * Creates a new thread and runs it.
     * @param task needs to be run on separate thread.
     */
    public void runNow(Task<Void> task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Randomly switches images with others.
     */
    private void changeImages() {
        Random rand = new Random();
        //Picks random index of photos and puts in a random location in tilePane
        int location = rand.nextInt(20);
        int newPhoto = rand.nextInt(200);
        //Replaces picture with a different one
        ItunesResult result = itunesResponse.results[newPhoto];
        Image newImage = new Image(result.artworkUrl100, 150, 150, true, true);
        Platform.runLater(() -> {
            this.pictures.getChildren().remove(location);
            this.pictures.getChildren().add(location, new ImageView(newImage));
        });
    }
} // GalleryApp
