import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class RegisterScene {
    private ToggleGroup userTypeGroup;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField nameField;
    private TextField phoneField;
    private TextField addressField;
    private TextField ageField;
    private TextField salaryField;
    private Label addressLabel;
    private Label salaryLabel;
    private GridPane formGrid;

    public Scene createScene() {
        VBox mainContainer = new VBox(20);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setPadding(new Insets(40));
        mainContainer.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label titleLabel = new Label("用户注册");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.WHITE);

        VBox formContainer = new VBox(15);
        formContainer.setAlignment(Pos.CENTER);
        formContainer.setPadding(new Insets(30));
        formContainer.setMaxWidth(500);
        formContainer.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");

        Label formTitle = new Label("创建账户");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        formTitle.setTextFill(Color.DARKSLATEBLUE);

        // 用户类型选择
        Label typeLabel = new Label("选择用户类型:");
        typeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        userTypeGroup = new ToggleGroup();
        RadioButton userRadio = new RadioButton("普通用户");
        RadioButton employeeRadio = new RadioButton("服务员工");
        userRadio.setToggleGroup(userTypeGroup);
        employeeRadio.setToggleGroup(userTypeGroup);
        userRadio.setSelected(true);

        HBox radioContainer = new HBox(20);
        radioContainer.setAlignment(Pos.CENTER);
        radioContainer.getChildren().addAll(userRadio, employeeRadio);

        // 创建表单字段容器
        formGrid = new GridPane();
        formGrid.setHgap(15);
        formGrid.setVgap(15);
        formGrid.setAlignment(Pos.CENTER);
        formGrid.setPadding(new Insets(10, 0, 10, 0));

        // 公共字段
        usernameField = createTextField("用户名");
        passwordField = createPasswordField("密码");
        nameField = createTextField("姓名");
        phoneField = createTextField("联系电话");
        ageField = createTextField("年龄");

        // 添加到网格 - 使用两列布局
        int row = 0;

        // 用户名
        Label usernameLabel = createFormLabel("用户名:");
        formGrid.add(usernameLabel, 0, row);
        formGrid.add(usernameField, 1, row++);

        // 密码
        Label passwordLabel = createFormLabel("密码:");
        formGrid.add(passwordLabel, 0, row);
        formGrid.add(passwordField, 1, row++);

        // 姓名
        Label nameLabel = createFormLabel("姓名:");
        formGrid.add(nameLabel, 0, row);
        formGrid.add(nameField, 1, row++);

        // 联系电话
        Label phoneLabel = createFormLabel("联系电话:");
        formGrid.add(phoneLabel, 0, row);
        formGrid.add(phoneField, 1, row++);

        // 年龄
        Label ageLabel = createFormLabel("年龄:");
        formGrid.add(ageLabel, 0, row);
        formGrid.add(ageField, 1, row++);

        // 用户特有字段 - 地址
        addressLabel = createFormLabel("居住地址:");
        addressField = createTextField("居住地址");
        formGrid.add(addressLabel, 0, row);
        formGrid.add(addressField, 1, row);
        int addressRow = row++; // 记录地址字段所在的行

        // 员工特有字段 - 薪资要求
        salaryLabel = createFormLabel("薪资要求:");
        salaryField = createTextField("薪资要求");
        // 初始状态下薪资字段不添加到网格中
        salaryLabel.setVisible(false);
        salaryField.setVisible(false);

        // 事件处理
        userRadio.setOnAction(e -> toggleUserType(true, addressRow));
        employeeRadio.setOnAction(e -> toggleUserType(false, addressRow));

        // 按钮容器
        HBox buttonContainer = new HBox(20);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setPadding(new Insets(20, 0, 0, 0));

        Button registerButton = new Button("注册");
        stylePrimaryButton(registerButton);
        registerButton.setPrefWidth(150);
        registerButton.setPrefHeight(45);

        Button backButton = new Button("返回登录");
        styleSecondaryButton(backButton);
        backButton.setPrefWidth(150);
        backButton.setPrefHeight(45);

        buttonContainer.getChildren().addAll(registerButton, backButton);

        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(400);
        errorLabel.setAlignment(Pos.CENTER);

        registerButton.setOnAction(e -> handleRegister(errorLabel));
        backButton.setOnAction(e -> AppManager.getInstance().showLoginScene());

        formContainer.getChildren().addAll(formTitle, typeLabel, radioContainer, formGrid, buttonContainer, errorLabel);

        mainContainer.getChildren().addAll(titleLabel, formContainer);

        return new Scene(mainContainer, 800, 750);
    }

    private TextField createTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefHeight(40);
        field.setPrefWidth(300);
        field.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 0 15; -fx-font-size: 14;");
        return field;
    }

    private PasswordField createPasswordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setPrefHeight(40);
        field.setPrefWidth(300);
        field.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 0 15; -fx-font-size: 14;");
        return field;
    }

    private Label createFormLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        label.setTextFill(Color.DARKSLATEGRAY);
        label.setPrefWidth(100);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
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
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #f8f9fa; -fx-text-fill: #5a6fd8; -fx-border-color: #5a6fd8; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-text-fill: #667eea; -fx-border-color: #667eea; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8; -fx-font-weight: bold; -fx-font-size: 14;"));
    }

    private void toggleUserType(boolean isUser, int addressRow) {
        if (isUser) {
            // 显示地址字段，隐藏薪资字段
            addressLabel.setVisible(true);
            addressField.setVisible(true);

            // 如果薪资字段在网格中，移除它
            if (formGrid.getChildren().contains(salaryLabel)) {
                formGrid.getChildren().removeAll(salaryLabel, salaryField);
            }

            // 确保地址字段在网格中
            if (!formGrid.getChildren().contains(addressLabel)) {
                formGrid.add(addressLabel, 0, addressRow);
                formGrid.add(addressField, 1, addressRow);
            }
        } else {
            // 隐藏地址字段，显示薪资字段
            addressLabel.setVisible(false);
            addressField.setVisible(false);

            // 如果地址字段在网格中，移除它
            if (formGrid.getChildren().contains(addressLabel)) {
                formGrid.getChildren().removeAll(addressLabel, addressField);
            }

            // 确保薪资字段在网格中
            if (!formGrid.getChildren().contains(salaryLabel)) {
                formGrid.add(salaryLabel, 0, addressRow);
                formGrid.add(salaryField, 1, addressRow);
            }

            salaryLabel.setVisible(true);
            salaryField.setVisible(true);
        }

        // 清空隐藏的字段
        if (isUser) {
            salaryField.setText("");
        } else {
            addressField.setText("");
        }
    }

    private void handleRegister(Label errorLabel) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String name = nameField.getText();
        String phone = phoneField.getText();
        String ageText = ageField.getText();

        if (username.isEmpty() || password.isEmpty() || name.isEmpty() || phone.isEmpty() || ageText.isEmpty()) {
            showError(errorLabel, "请填写所有必填字段");
            return;
        }

        // 验证年龄
        int age;
        try {
            age = Integer.parseInt(ageText);
            if (age <= 0 || age > 120) {
                showError(errorLabel, "请输入有效的年龄(1-120)");
                return;
            }
        } catch (NumberFormatException e) {
            showError(errorLabel, "年龄必须是有效数字");
            return;
        }

        DataManager dataManager = AppManager.getInstance().getDataManager();
        if (dataManager.usernameExists(username)) {
            showError(errorLabel, "用户名已存在");
            return;
        }

        boolean isUser = ((RadioButton)userTypeGroup.getSelectedToggle()).getText().equals("普通用户");

        if (isUser) {
            String address = addressField.getText();
            if (address.isEmpty()) {
                showError(errorLabel, "请填写居住地址");
                return;
            }
            User user = new User(username, password, name, phone, address, age);
            dataManager.addUser(user);
            showSuccess("用户注册成功！");
        } else {
            String salaryText = salaryField.getText();
            if (salaryText.isEmpty()) {
                showError(errorLabel, "请填写薪资要求");
                return;
            }

            try {
                double salary = Double.parseDouble(salaryText);
                if (salary < 0) {
                    showError(errorLabel, "薪资不能为负数");
                    return;
                }
                Employee employee = new Employee(username, password, name, age, phone, salary);
                dataManager.addEmployee(employee);
                showSuccess("员工注册成功！");
            } catch (NumberFormatException e) {
                showError(errorLabel, "薪资必须是有效数字");
                return;
            }
        }

        AppManager.getInstance().showLoginScene();
    }

    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("成功");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}