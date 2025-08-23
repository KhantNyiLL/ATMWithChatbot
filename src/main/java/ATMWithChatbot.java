import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ATMWithChatbot extends Application {

    private HashMap<String, User> users = new HashMap<>();
    private User currentUser;

    private TextArea chatbotArea = new TextArea();
    private TextField chatbotInput = new TextField();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        try {
            URL url = getClass().getResource("/VAULT_X-LOGO.png");
            if (url != null) {
                Image icon = new Image(url.toExternalForm());
                primaryStage.getIcons().add(icon);
            }
        } catch (Exception ex) {
            System.err.println("⚠ Icon load failed: " + ex.getMessage());
        }

        // Sample user
        users.put("1234", new User("1234", 1000));

        //login Scene
        Label pinLabel = new Label("Enter PIN:");
        PasswordField pinField = new PasswordField();
        Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("btn-atm");

        VBox loginLayout = new VBox(12, pinLabel, pinField, loginButton);
        loginLayout.setPadding(new Insets(20));
        loginLayout.setPrefSize(320, 180);
        loginLayout.getStyleClass().add("panel-login");
        Scene loginScene = new Scene(loginLayout);

      //atmscence
        Button checkBalanceButton = new Button("Check Balance");
        Button depositButton = new Button("Deposit");
        Button withdrawButton = new Button("Withdraw");
        Button viewTransactionsButton = new Button("View Transactions");
        Button logoutButton = new Button("Logout");

        checkBalanceButton.getStyleClass().add("btn-atm");
        depositButton.getStyleClass().add("btn-atm");
        withdrawButton.getStyleClass().add("btn-atm");
        viewTransactionsButton.getStyleClass().add("btn-atm");
        logoutButton.getStyleClass().add("btn-atm");

        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount");
        amountField.getStyleClass().add("terminal-input");

        VBox atmButtons = new VBox(12,
                amountField,
                checkBalanceButton,
                depositButton,
                withdrawButton,
                viewTransactionsButton,
                logoutButton
        );
        atmButtons.setPadding(new Insets(16));
        atmButtons.setPrefWidth(280);
        atmButtons.getStyleClass().add("panel-left");

        chatbotArea.setEditable(false);
        chatbotArea.getStyleClass().add("terminal-textarea");
        chatbotInput.setPromptText("Ask something...");
        chatbotInput.setPrefWidth(300);
        chatbotInput.getStyleClass().add("terminal-input");
        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("btn-atm");

        HBox chatbotBox = new HBox(8, chatbotInput, sendButton);
        VBox chatbotLayout = new VBox(10, new Label("Chatbot Assistant:"), chatbotArea, chatbotBox);
        chatbotLayout.setPadding(new Insets(16));
        VBox.setVgrow(chatbotArea, Priority.ALWAYS);
        chatbotLayout.getStyleClass().add("panel-chat");

        HBox mainRow = new HBox(20, atmButtons, chatbotLayout);
        mainRow.setPadding(new Insets(16));

        // logo&title
        ImageView logoView = new ImageView();
        logoView.setFitWidth(36);
        logoView.setFitHeight(36);
        logoView.setPreserveRatio(true);
        try {
            URL url = getClass().getResource("/VAULT_X-LOGO.png");
            if (url != null) logoView.setImage(new Image(url.toExternalForm()));
        } catch (Exception ignored) {}

        Label titleLabel = new Label("VAULT-X");
        titleLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        titleLabel.getStyleClass().add("title-atm");

        HBox header = new HBox(12, logoView, titleLabel);
        header.setPadding(new Insets(12));
        header.getStyleClass().add("header-atm");

        VBox root = new VBox(header, mainRow);
        root.getStyleClass().add("root-atm");
        Scene atmScene = new Scene(root, 960, 600);


        URL css = getClass().getResource("/styles.css");
        if (css != null) {
            atmScene.getStylesheets().add(css.toExternalForm());
        } else {
            System.err.println("⚠ styles.css not found at /resources/styles.css");
        }

        // --- Logic ---
        loginButton.setOnAction(e -> {
            String pin = pinField.getText();
            if (users.containsKey(pin)) {
                currentUser = users.get(pin);
                primaryStage.setScene(atmScene);
                pinField.clear();
            } else {
                showAlert("Login Failed", "Invalid PIN");
            }
        });

        checkBalanceButton.setOnAction(e -> {
            String message = "Your balance is: $" + fmt(currentUser.getBalance());
            showAlert("Balance", message);
            currentUser.addTransaction(message);
        });

        depositButton.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                if (amount > 0) {
                    currentUser.deposit(amount);
                    String message = "$" + fmt(amount) + " added to your account.";
                    showAlert("Deposited", message);
                    currentUser.addTransaction("Deposited: $" + fmt(amount));
                } else {
                    showAlert("Invalid Input", "Please enter a positive amount.");
                }
            } catch (NumberFormatException ex) {
                showAlert("Error", "Please enter a valid number.");
            }
        });

        withdrawButton.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                if (amount > 0 && currentUser.withdraw(amount)) {
                    String message = "$" + fmt(amount) + " withdrawn from your account.";
                    showAlert("Withdrawn", message);
                    currentUser.addTransaction("Withdrawn: $" + fmt(amount));
                } else {
                    showAlert("Failed", "Insufficient balance or invalid amount.");
                }
            } catch (NumberFormatException ex) {
                showAlert("Error", "Please enter a valid number.");
            }
        });

        viewTransactionsButton.setOnAction(e -> {
            Alert transactionAlert = new Alert(Alert.AlertType.INFORMATION);
            transactionAlert.setTitle("Transaction History");
            transactionAlert.setHeaderText("Your Transactions");

            StringBuilder historyText = new StringBuilder();
            for (String entry : currentUser.getTransactionHistory()) {
                historyText.append(entry).append("\n");
            }
            transactionAlert.setContentText(historyText.toString());
            transactionAlert.showAndWait();
        });

        logoutButton.setOnAction(e -> primaryStage.setScene(loginScene));

        sendButton.setOnAction(e -> handleChatbot());
        chatbotInput.setOnAction(e -> handleChatbot());

        // --- Stage ---
        primaryStage.setTitle("VAULT-X");
        primaryStage.setScene(loginScene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }


    private void handleChatbot() {
        String raw = chatbotInput.getText().trim();
        String input = raw.toLowerCase(Locale.ROOT);
        String response;

        // Extract amount if present (e.g., 500, $1,000, 250.75)
        Double amount = extractAmount(raw);

        if (containsAny(input, "balance", "check balance", "show balance", "how much")) {
            response = "Your current balance is $" + fmt(currentUser.getBalance());
            currentUser.addTransaction("Checked balance: $" + fmt(currentUser.getBalance()));

        } else if (containsAny(input, "deposit", "top up", "top-up", "add", "credit", "put", "load")) {
            double amt = (amount != null ? amount : 100.0); // default to 100 if amount not specified
            if (amt <= 0) {
                response = "Please enter a positive amount to deposit.";
            } else {
                currentUser.deposit(amt);
                response = "Deposited $" + fmt(amt) + " to your account.";
                currentUser.addTransaction("Chatbot Deposited: $" + fmt(amt));
            }

        } else if (containsAny(input, "withdraw", "take out", "cash out", "remove", "debit", "deduct")) {
            double amt = (amount != null ? amount : 100.0); // default to 100 if amount not specified
            if (amt <= 0) {
                response = "Please enter a positive amount to withdraw.";
            } else if (currentUser.withdraw(amt)) {
                response = "Withdrew $" + fmt(amt) + " from your account.";
                currentUser.addTransaction("Chatbot Withdrawn: $" + fmt(amt));
            } else {
                response = "Insufficient funds to withdraw $" + fmt(amt) + ".";
            }

        } else if (containsAny(input, "hello", "hi", "hey")) {
            response = "Hello! You can say 'deposit 500', 'withdraw 250', or 'check balance'.";
        } else {
            response = "Sorry, I didn't understand. Try 'deposit 500', 'withdraw 200', or 'check balance'.";
        }

        chatbotArea.appendText("You: " + raw + "\nAI: " + response + "\n");
        chatbotInput.clear();
    }

    /** Extracts the first numeric amount from text like "$1,200.50" or "deposit 500"; returns null if none. */
    private Double extractAmount(String text) {
        Pattern p = Pattern.compile("\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\\.[0-9]{1,2})?");
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        String num = m.group();
        num = num.replaceAll("[,$\\s]", "");
        try {
            return Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private boolean containsAny(String input, String... keys) {
        for (String k : keys) {
            if (input.contains(k)) return true;
        }
        return false;
    }

    /* 2 decimals. */
    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    static class User {
        private String pin;
        private double balance;
        private ArrayList<String> transactionHistory = new ArrayList<>();

        public User(String pin, double balance) {
            this.pin = pin;
            this.balance = balance;
        }

        public double getBalance() {
            return balance;
        }

        public void deposit(double amount) {
            balance += amount;
        }

        public boolean withdraw(double amount) {
            if (amount <= balance) {
                balance -= amount;
                return true;
            }
            return false;
        }

        public void addTransaction(String record) {
            transactionHistory.add(record);
        }

        public ArrayList<String> getTransactionHistory() {
            return transactionHistory;
        }
    }
}
