import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;

import static javax.management.remote.JMXConnectorFactory.connect;

/**
 * VAULT-X : ATM + Rule-based Chatbot
 * Adds:
 * - Sign Up (username + password)
 * - Secure password storage (salted SHA-256)
 * - Persistent storage of users & balances (~/.vaultx_users.db)
 */
public class ATMWithChatbot extends Application {

    /* ========= Persistence ========= */

    // Simple, human-readable line format (one user per line):
    // username|base64(salt)|base64(sha256(salt+password))|balance
    // Usernames must not contain '|'
    private static final Path DB_PATH =
            Paths.get(System.getProperty("user.home"), ".vaultx_users.db");

    /* ========= Data Model ========= */

    static class User {
        private final String username;
        private final byte[] salt;
        private final byte[] passwordHash;
        private double balance;
//        private final List<String> transactionHistory = new ArrayList<>();

        User(String username, byte[] salt, byte[] passwordHash, double balance) {
            this.username = username;
            this.salt = salt;
            this.passwordHash = passwordHash;
            this.balance = balance;
        }

        public String getUsername() {
            return username;
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

        //remove in memory transaction history heree ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        public void addTransaction(String record) { transactionHistory.add(record); }
//        public List<String> getTransactionHistory() { return transactionHistory; }
        public byte[] getSalt() {
            return salt;
        }

        public byte[] getPasswordHash() {
            return passwordHash;
        }
    }

    //Load user from SQLite ////////////////////////////////////////////////
    private User loadUserFromDB(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DBHelper.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                byte[] salt = rs.getBytes("salt");
                byte[] hash = rs.getBytes("password_hash");
                double balance = rs.getDouble("balance");
                return new User(username, salt, hash, balance);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    //Save/update user to DB///////////////////////////////////////////////////////////////////////////////////////////
    private void saveUserToDB(User u) {
        String sql = "INSERT OR REPLACE INTO users (username, salt, password_hash, balance) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBHelper.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, u.getUsername().toLowerCase());
            pstmt.setBytes(2, u.getSalt());
            pstmt.setBytes(3, u.getPasswordHash());
            pstmt.setDouble(4, u.getBalance());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //Transactions in DB//////////////////////////////////////////////////////////////////////////////////////////////////////
    private void saveTransactionToDB(User u, String action) {
        String sql = "INSERT INTO transactions (username, timestamp, action) VALUES (?, ?, ?)";
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.LocalDateTime.now());
        try (Connection conn = DBHelper.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, u.getUsername().toLowerCase());
            pstmt.setString(2, timestamp);
            pstmt.setString(3, action);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    //Fetch transactions////////////////////////////////////////////////////////////////////////////////////////////////////////
    private java.util.List<String> fetchTransactions(User u) {
        java.util.List<String> txs = new java.util.ArrayList<>();
        String sql = "SELECT timestamp, action FROM transactions WHERE username = ? ORDER BY id ASC";
        try (Connection conn = DBHelper.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, u.getUsername().toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                txs.add("[" + rs.getString("timestamp") + "] " + rs.getString("action"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return txs;
    }

//    private void refreshTxList() {
//        if (currentUser == null) {
//            txList.setItems(javafx.collections.FXCollections.observableArrayList());
//            return;
//        }
//        txList.setItems(javafx.collections.FXCollections.observableArrayList(fetchTransactions(currentUser)));
//        txList.scrollTo(txList.getItems().size() - 1);
//    }


    private final Map<String, User> users = new HashMap<>();
//    private String currentPin;  // track logged-in user//////////////////////////////////////////////////////////////////////////////////////////////

    private User currentUser;

    /* ========= UI Controls ========= */

    // Shared
    private Scene loginScene;
    private Scene atmScene;

    // Login/Signup UI
    private TextField loginUsernameField;
    private PasswordField loginPasswordField;
    private TextField signupUsernameField;
    private PasswordField signupPasswordField;
    private PasswordField signupConfirmField;

    // ATM / Chatbot UI
    private final TextArea chatbotArea = new TextArea();
    private final TextField chatbotInput = new TextField();
    private final TextField amountField = new TextField();
    private final ListView<String> txList = new ListView<>();
    private final Label currentUserLabel = new Label("");

    /* ========= App ========= */

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // Load users from disk (if file exists)
//        loadUsersFromDisk();
        DBHelper.initializeDatabase();

        // ---- Login & Signup Scene ----
        TabPane authTabs = new TabPane();
        authTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Login tab
        VBox loginBox = buildLoginBox(stage);
        Tab loginTab = new Tab("Login", loginBox);

        // Sign Up tab
        VBox signupBox = buildSignupBox();
        Tab signupTab = new Tab("Sign Up", signupBox);

        authTabs.getTabs().addAll(loginTab, signupTab);

        VBox loginRoot = new VBox(authTabs);
        loginRoot.setPadding(new Insets(16));
        loginRoot.getStyleClass().add("root-atm");
        loginScene = new Scene(loginRoot, 520, 360);

        // ---- ATM Scene ----
        VBox atmRoot = buildAtmRoot(stage);
        atmScene = new Scene(atmRoot, 1100, 620);

        // ---- CSS ----
        URL css = getClass().getResource("/styles.css");
        if (css != null) {
            loginScene.getStylesheets().add(css.toExternalForm());
            atmScene.getStylesheets().add(css.toExternalForm());
        }

        // ---- Stage ----
        try {
            URL iconUrl = getClass().getResource("/VAULT_X-LOGO.png");
            if (iconUrl != null) stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        } catch (Exception ignored) {
        }
        stage.setTitle("VAULT-X");
        stage.setScene(loginScene);
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        stage.show();
    }

    /* ========= UI Builders ========= */

    private VBox buildLoginBox(Stage stage) {
        Label title = new Label("Welcome to VAULT-X");
        title.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        title.getStyleClass().add("title-atm");

        loginUsernameField = new TextField();
        loginUsernameField.setPromptText("Username");

        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("btn-atm");

        VBox box = new VBox(12, title, loginUsernameField, loginPasswordField, loginBtn);
        box.setPadding(new Insets(16));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("panel-login");

        loginBtn.setOnAction(e -> doLogin(stage));
        loginPasswordField.setOnAction(e -> doLogin(stage));

        return box;
    }

    private VBox buildSignupBox() {
        Label title = new Label("Create a New Account");
        title.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
        title.getStyleClass().add("title-atm");

        signupUsernameField = new TextField();
        signupUsernameField.setPromptText("Choose a username");

        signupPasswordField = new PasswordField();
        signupPasswordField.setPromptText("Choose a password");

        signupConfirmField = new PasswordField();
        signupConfirmField.setPromptText("Confirm password");

        Button createBtn = new Button("Create Account");
        createBtn.getStyleClass().add("btn-atm");

        //  Make Enter key trigger the button
        createBtn.setDefaultButton(true);
        loginUsernameField.setOnAction(e -> loginPasswordField.requestFocus());


        VBox box = new VBox(12,
                title,
                new Label("Username"), signupUsernameField,
                new Label("Password"), signupPasswordField,
                new Label("Confirm Password"), signupConfirmField,
                createBtn
        );
        box.setPadding(new Insets(16));
        box.getStyleClass().add("panel-login");

        createBtn.setOnAction(e -> doSignup());

        return box;
    }

    private VBox buildAtmRoot(Stage stage) {
        // Left panel (ATM controls)
        Button checkBalanceButton = new Button("Check Balance");
        Button depositButton = new Button("Deposit");
        Button withdrawButton = new Button("Withdraw");
        Button viewTransactionsButton = new Button("View Transactions");
        Button logoutButton = new Button("Logout");

        for (Button b : Arrays.asList(checkBalanceButton, depositButton, withdrawButton, viewTransactionsButton, logoutButton)) {
            b.getStyleClass().add("btn-atm");
        }

        amountField.setPromptText("Enter amount");
        amountField.getStyleClass().add("terminal-input");

        amountField.setOnAction(e -> {
            if (!requireLoginOrWarn()) return;

            Double amt = parseAmount(amountField.getText());
            if (amt == null || amt <= 0) {
                showWarn("Invalid Input", "Enter a positive amount.");
                return;
            }

            // Show popup asking Deposit or Withdraw
            Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
            choice.setTitle("Choose Action");
            choice.setHeaderText("Amount entered: $" + fmt(amt));
            choice.setContentText("Do you want to Deposit or Withdraw?");

            ButtonType depositBtn = new ButtonType("Deposit");
            ButtonType withdrawBtn = new ButtonType("Withdraw");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            choice.getButtonTypes().setAll(depositBtn, withdrawBtn, cancelBtn);

            Optional<ButtonType> result = choice.showAndWait();

            if (result.isPresent()) {
                if (result.get() == depositBtn) {
                    currentUser.deposit(amt);
                    addTx("Deposited: $" + fmt(amt));
                    showInfo("Deposited", "$" + fmt(amt) + " added.");
                } else if (result.get() == withdrawBtn) {
                    if (currentUser.withdraw(amt)) {
                        addTx("Withdrawn: $" + fmt(amt));
                        showInfo("Withdrawn", "$" + fmt(amt) + " withdrawn.");
                    } else {
                        showWarn("Failed", "Insufficient balance.");
                    }
                }
            }

            amountField.clear();
            refreshTxList();
            saveUserToDB(currentUser); // persist
        });

        /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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

        // Chatbot
        chatbotArea.setEditable(false);
        chatbotArea.getStyleClass().add("terminal-textarea");

        chatbotInput.setPromptText("Try: deposit 500, withdraw 200, balance, help");
        chatbotInput.setPrefWidth(300);
        chatbotInput.getStyleClass().add("terminal-input");
        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("btn-atm");
        HBox chatbotBox = new HBox(8, chatbotInput, sendButton);

        VBox chatbotLayout = new VBox(10, new Label("Chatbot Assistant:"), chatbotArea, chatbotBox);
        chatbotLayout.setPadding(new Insets(16));
        VBox.setVgrow(chatbotArea, Priority.ALWAYS);
        chatbotLayout.getStyleClass().add("panel-chat");

        // Transactions panel
        txList.setPlaceholder(new Label("No transactions yet"));
        txList.setFocusTraversable(false);
        VBox txPanel = new VBox(8, new Label("Transactions:"), txList);
        txPanel.setPadding(new Insets(16));
        txPanel.getStyleClass().add("panel-chat");

        HBox mainRow = new HBox(20, atmButtons, chatbotLayout, txPanel);
        mainRow.setPadding(new Insets(16));

        // Header
        ImageView logoView = new ImageView();
        logoView.setFitWidth(36);
        logoView.setFitHeight(36);
        logoView.setPreserveRatio(true);
        try {
            URL url = getClass().getResource("/VAULT_X-LOGO.png");
            if (url != null) logoView.setImage(new Image(url.toExternalForm()));
        } catch (Exception ignored) {
        }

        Label titleLabel = new Label("VAULT-X");
        titleLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
        titleLabel.getStyleClass().add("title-atm");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        currentUserLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

        HBox header = new HBox(12, logoView, titleLabel, spacer, currentUserLabel);
        header.setPadding(new Insets(12));
        header.getStyleClass().add("header-atm");

        // Actions
        checkBalanceButton.setOnAction(e -> {
            if (!requireLoginOrWarn()) return;
            String message = "Your balance is: $" + fmt(currentUser.getBalance());
            showInfo("Balance", message);
            addTx("Checked balance: $" + fmt(currentUser.getBalance()));
        });

        depositButton.setOnAction(e -> {
            if (!requireLoginOrWarn()) return;
            Double amt = parseAmount(amountField.getText());
            if (amt == null || amt <= 0) {
                showWarn("Invalid Input", "Enter a positive amount.");
                return;
            }
            currentUser.deposit(amt);
            addTx("Deposited: $" + fmt(amt));
            showInfo("Deposited", "$" + fmt(amt) + " added.");
            amountField.clear();
            refreshTxList();
            saveUserToDB(currentUser); // persist
        });

        withdrawButton.setOnAction(e -> {
            if (!requireLoginOrWarn()) return;
            Double amt = parseAmount(amountField.getText());
            if (amt == null || amt <= 0) {
                showWarn("Invalid Input", "Enter a positive amount.");
                return;
            }
            if (currentUser.withdraw(amt)) {
                addTx("Withdrawn: $" + fmt(amt));
                showInfo("Withdrawn", "$" + fmt(amt) + " withdrawn.");
                amountField.clear();
                refreshTxList();
                saveUserToDB(currentUser); // persist
            } else {
                showWarn("Failed", "Insufficient balance.");
            }
        });

        viewTransactionsButton.setOnAction(
                e -> refreshTxList());

        logoutButton.setOnAction(e -> {
            if (currentUser != null)
                saveUserToDB(currentUser);

            currentUser = null;
            currentUserLabel.setText("");
            amountField.clear();
            chatbotArea.clear();
            txList.getItems().clear();

            loginUsernameField.clear();
            loginPasswordField.clear();

            ((Stage) header.getScene().getWindow()).setScene(loginScene);
        });

        sendButton.setOnAction(e -> handleChatbot());
        chatbotInput.setOnAction(e -> handleChatbot());

        VBox root = new VBox(header, mainRow);
        root.getStyleClass().add("root-atm");
        return root;
    }

    /* ========= Auth Actions ========= */

    private void doLogin(Stage stage) {
        String username = safe(loginUsernameField.getText());
        String password = loginPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showWarn("Login", "Enter username and password.");
            return;
        }
        User u = loadUserFromDB(username); ////////////////////////////////////////////////////
        if (u == null) {
            showWarn("Login", "User not found.");
            return;
        }
        if (verifyPassword(u.getSalt(), u.getPasswordHash(), password)) {
            currentUser = u;
            updateCurrentUserUI();
            chatbotArea.clear();
            amountField.clear();
            refreshTxList();
            Stage st = stage != null ? stage : (Stage) loginScene.getWindow();
            st.setScene(atmScene);
        } else {
            showWarn("Login", "Incorrect password.");
        }
    }

    private void doSignup() {
        String username = safe(signupUsernameField.getText());
        String pw = signupPasswordField.getText();
        String pw2 = signupConfirmField.getText();

        if (username.isEmpty() || pw.isEmpty() || pw2.isEmpty()) {
            showWarn("Sign Up", "Fill all fields.");
            return;
        }
        if (username.contains("|")) {
            showWarn("Sign Up", "Username cannot contain '|'.");
            return;
        }
        if (!pw.equals(pw2)) {
            showWarn("Sign Up", "Passwords do not match.");
            return;
        }
        if (users.containsKey(username.toLowerCase(Locale.ROOT)) || loadUserFromDB(username) != null) {
            showWarn("Sign Up", "Username already exists.");
            return;
        }

        // Optional: basic password strength
        if (pw.length() < 6) {
            showWarn("Sign Up", "Use at least 6 characters.");
            return;
        }

        byte[] salt = randomSalt();
        byte[] hash = hashPassword(salt, pw);
        User u = new User(username, salt, hash, 0.0);
//        users.put(username.toLowerCase(Locale.ROOT), u);
        saveUserToDB(u);
        showInfo("Sign Up", "Account created. You can log in now.");
        // Clear fields
        signupUsernameField.clear();
        signupPasswordField.clear();
        signupConfirmField.clear();
    }

    /* ========= Chatbot ========= */

    private void handleChatbot() {
        String raw = chatbotInput.getText().trim();
        if (raw.isEmpty()) return;

        String input = raw.toLowerCase(Locale.ROOT);
        String response;
        Double amount = extractAmount(raw);

        if (containsAny(input, "help", "commands", "menu")) {
            response = String.join("\n",
                    "I can do:",
                    "• balance — show your balance",
                    "• deposit <amount> — add money",
                    "• withdraw <amount> — take money out",
                    "• history — show transactions",
                    "• clear — clear chat",
                    "• logout — log out"
            );

        } else if (containsAny(input, "history", "transactions", "recent")) {
            if (!requireLoginOrWarn()) {
                chatbotInput.clear();
                return;
            }
            response = "Showing your transactions (right panel).";
            refreshTxList();

        } else if (containsAny(input, "clear", "cls")) {
            chatbotArea.clear();
            response = "Cleared.";

        } else if (containsAny(input, "logout", "sign out", "signout")) {
            response = "You have been logged out.";

        } else if (containsAny(input, "balance", "check balance", "show balance", "how much")) {
            if (!requireLoginOrWarn()) {
                chatbotInput.clear();
                return;
            }
            response = "Your current balance is $" + fmt(currentUser.getBalance());
            addTx("Checked balance: $" + fmt(currentUser.getBalance()));

        } else if (containsAny(input, "deposit", "top up", "top-up", "add", "credit", "put", "load")) {
            if (!requireLoginOrWarn()) {
                chatbotInput.clear();
                return;
            }
            double amt = (amount != null ? amount : 100.0);
            if (amt <= 0) {
                response = "Enter a positive amount.";
            } else {
                currentUser.deposit(amt);
                addTx("Chatbot deposited: $" + fmt(amt));
                refreshTxList();
                saveUserToDB(currentUser);
                response = "Deposited $" + fmt(amt) + ".";
            }

        } else if (containsAny(input, "withdraw", "take out", "take", "minus")) {
            if (!requireLoginOrWarn()) {
                chatbotInput.clear();
                return;
            }
            double amt = (amount != null ? amount : 100.0);
            if (amt <= 0) {
                response = "Enter a positive amount.";
            } else if (currentUser.withdraw(amt)) {
                addTx("Chatbot withdrew: $" + fmt(amt));
                refreshTxList();
                saveUserToDB(currentUser);
                response = "Withdrew $" + fmt(amt) + ".";
            } else {
                response = "Insufficient balance.";
            }

        } else {
            response = "Sorry, I didn't understand. Type 'help' to see commands.";
        }

        chatbotArea.appendText("You: " + raw + "\nAI: " + response + "\n\n");
        chatbotInput.clear();
    }

    /* ========= Helpers ========= */

    private void updateCurrentUserUI() {
        if (currentUser == null) currentUserLabel.setText("");
        else currentUserLabel.setText("Logged in: " + currentUser.getUsername());
    }

    private boolean requireLoginOrWarn() {
        if (currentUser == null) {
            showWarn("Not logged in", "Please log in first.");
            return false;
        }
        return true;
    }

    private void addTx(String text) {
        if (currentUser != null) {
            // Save to DB
            saveTransactionToDB(currentUser, text);
            // Refresh transaction list
            refreshTxList();
        }
    }


    // NEW SQLITE VERSION
    private void refreshTxList() {
        if (currentUser == null) {
            txList.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> txs = fetchTransactions(currentUser); // uses your existing method

        ObservableList<String> items = FXCollections.observableArrayList(txs);
        txList.setItems(items);

        if (!items.isEmpty()) {
            txList.scrollTo(items.size() - 1);
        }
    }


    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showWarn(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private boolean containsAny(String input, String... keys) {
        for (String k : keys) if (input.contains(k)) return true;
        return false;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private Double parseAmount(String text) {
        try {
            return extractAmount(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts first number from text like "$1,200.50"
     */
    private Double extractAmount(String text) {
        if (text == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\\.[0-9]{1,2})?");
        java.util.regex.Matcher m = p.matcher(text);
        if (!m.find()) return null;
        String num = m.group().replaceAll("[,$\\s]", "");
        try {
            return Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ========= Passwords ========= */

    private byte[] randomSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private byte[] hashPassword(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Hashing error", e);
        }
    }

    private boolean verifyPassword(byte[] salt, byte[] expectedHash, String candidatePassword) {
        return Arrays.equals(expectedHash, hashPassword(salt, candidatePassword));
    }

    /* ========= Persistence (File I/O) ========= */

    private void loadUsersFromDisk() {
        users.clear();
        if (!Files.exists(DB_PATH)) {
            // Optional: seed demo accounts (for convenience)
            byte[] s1 = randomSalt();
            byte[] h1 = hashPassword(s1, "1234");
            users.put("alex".toLowerCase(Locale.ROOT), new User("alex", s1, h1, 1000));
            byte[] s2 = randomSalt();
            byte[] h2 = hashPassword(s2, "5678");
            users.put("sam".toLowerCase(Locale.ROOT), new User("sam", s2, h2, 750));
            saveUserToDB(currentUser);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(DB_PATH, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length != 4) continue;
                String username = parts[0];
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] hash = Base64.getDecoder().decode(parts[2]);
                double balance = Double.parseDouble(parts[3]);
                users.put(username.toLowerCase(Locale.ROOT), new User(username, salt, hash, balance));
            }
        } catch (Exception e) {
            e.printStackTrace();
            // If file corrupt, start fresh (but keep the file as-is).
        }
    }

//    private void saveUsersToDisk() {
//        try {
//            List<String> lines = new ArrayList<>();
//            lines.add("# VAULT-X users database");
//            for (User u : users.values()) {
//                String saltB64 = Base64.getEncoder().encodeToString(u.getSalt());
//                String hashB64 = Base64.getEncoder().encodeToString(u.getPasswordHash());
//                String line = String.join("|",
//                        u.getUsername(),
//                        saltB64,
//                        hashB64,
//                        String.format(Locale.US, "%.2f", u.getBalance())
//                );
//                lines.add(line);
//            }
//            Files.write(DB_PATH, lines, StandardCharsets.UTF_8);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public static void createUser(String pin, String password) {
        String sql = "INSERT OR IGNORE INTO users (pin, balance) VALUES (?, 0)";
        try (Connection conn = DBHelper.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pin);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
