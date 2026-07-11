import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class LoginScene {
    private TextField usernameField;
    private PasswordField passwordField;

    public Scene createScene() {
        // 创建主容器
        VBox mainContainer = new VBox(20);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setPadding(new Insets(40));
        mainContainer.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        // 标题
        Label titleLabel = new Label("老年人服务预约系统");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.WHITE);

        // 登录表单容器
        VBox formContainer = new VBox(15);
        formContainer.setAlignment(Pos.CENTER);
        formContainer.setPadding(new Insets(30));
        formContainer.setMaxWidth(400);
        formContainer.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");

        Label formTitle = new Label("用户登录");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        formTitle.setTextFill(Color.DARKSLATEBLUE);

        usernameField = new TextField();
        usernameField.setPromptText("用户名");
        styleTextField(usernameField);

        passwordField = new PasswordField();
        passwordField.setPromptText("密码");
        styleTextField(passwordField);

        Button loginButton = new Button("登录");
        stylePrimaryButton(loginButton);
        loginButton.setPrefWidth(200);

        Button registerButton = new Button("前往注册");
        styleSecondaryButton(registerButton);

        // 错误信息标签
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setVisible(false);

        // 按钮事件
        loginButton.setOnAction(e -> handleLogin(errorLabel));
        registerButton.setOnAction(e -> AppManager.getInstance().showRegisterScene());

        formContainer.getChildren().addAll(formTitle, usernameField, passwordField, loginButton, registerButton, errorLabel);

        mainContainer.getChildren().addAll(titleLabel, formContainer);

        return new Scene(mainContainer, 800, 600);
    }

    private void styleTextField(TextField field) {
        field.setPrefHeight(45);
        field.setPrefWidth(300);
        field.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 0 15;");
        field.setFont(Font.font(14));
    }

    private void stylePrimaryButton(Button button) {
        button.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;");
        button.setPrefHeight(45);
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #5a6fd8; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;"));
    }

    private void styleSecondaryButton(Button button) {
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: #667eea; -fx-border-color: #667eea; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;");
        button.setPrefHeight(45);
        button.setPrefWidth(200);
    }

    private void handleLogin(Label errorLabel) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(errorLabel, "请输入用户名和密码");
            return;
        }

        DataManager dataManager = AppManager.getInstance().getDataManager();
        User user = dataManager.authenticateUser(username, password);
        Employee employee = dataManager.authenticateEmployee(username, password);

        if (user != null) {
            AppManager.getInstance().showUserDashboard(user);
        } else if (employee != null) {
            AppManager.getInstance().showEmployeeDashboard(employee);
        } else {
            showError(errorLabel, "用户名或密码错误");
        }
    }

    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
