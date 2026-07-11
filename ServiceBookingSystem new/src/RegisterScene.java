import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.regex.Pattern;

public class RegisterScene {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,20}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
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
        usernameField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 20 ? c : null));
        passwordField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 32 ? c : null));
        phoneField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,11}") ? c : null));
        ageField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,3}") ? c : null));

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
        salaryField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,8}(\\.\\d{0,2})?") ? c : null));
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

        usernameField.textProperty().addListener((o, oldValue, value) -> {
            String text = value.trim();
            if (!text.isEmpty() && !USERNAME_PATTERN.matcher(text).matches()) {
                showError(errorLabel, "用户名需为4-20位字母、数字或下划线");
            } else {
                hideError(errorLabel);
            }
        });
        usernameField.focusedProperty().addListener((o, oldValue, focused) -> {
            String text = usernameField.getText().trim();
            if (!focused && USERNAME_PATTERN.matcher(text).matches()) {
                DataManager dm = AppManager.getInstance().getDataManager();
                if (!dm.isDatabaseAvailable()) {
                    showError(errorLabel, "数据库连接失败，请先配置数据库账号和密码");
                } else if (dm.usernameExists(text)) {
                    showError(errorLabel, "该用户名已被注册");
                } else {
                    hideError(errorLabel);
                }
            }
        });
        passwordField.textProperty().addListener((o, oldValue, value) -> {
            if (!value.isEmpty() && (value.length() < 8 || !value.matches(".*[A-Za-z].*") || !value.matches(".*\\d.*"))) {
                showError(errorLabel, "密码需为8-32位，并同时包含字母和数字");
            } else {
                hideError(errorLabel);
            }
        });
        phoneField.textProperty().addListener((o, oldValue, value) -> {
            if (!value.isEmpty() && value.length() == 11 && !PHONE_PATTERN.matcher(value).matches()) {
                showError(errorLabel, "手机号格式不正确");
            } else if (value.length() <= 11) {
                hideError(errorLabel);
            }
        });
        ageField.textProperty().addListener((o, oldValue, value) -> {
            if (!value.isEmpty()) {
                int age = Integer.parseInt(value);
                boolean employee = !((RadioButton) userTypeGroup.getSelectedToggle()).getText().equals("普通用户");
                if ((!employee && (age < 1 || age > 120)) || (employee && (age < 18 || age > 100))) {
                    showError(errorLabel, employee ? "员工年龄需为18-100岁" : "年龄需为1-120岁");
                } else {
                    hideError(errorLabel);
                }
            }
        });

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
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String ageText = ageField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || name.isEmpty() || phone.isEmpty() || ageText.isEmpty()) {
            showError(errorLabel, "请填写所有必填字段");
            return;
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            showError(errorLabel, "用户名必须为4-20位字母、数字或下划线");
            return;
        }
        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            showError(errorLabel, "密码必须为8-32位，并同时包含字母和数字");
            return;
        }
        if (name.length() < 2 || name.length() > 50) {
            showError(errorLabel, "姓名长度必须为2-50个字符");
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            showError(errorLabel, "请输入有效的11位手机号码");
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
        if (!dataManager.isDatabaseAvailable()) {
            showError(errorLabel, "数据库连接失败：请配置 SERVICE_DB_USER 和 SERVICE_DB_PASSWORD");
            return;
        }
        if (dataManager.usernameExists(username)) {
            showError(errorLabel, "用户名已存在");
            return;
        }

        boolean isUser = ((RadioButton)userTypeGroup.getSelectedToggle()).getText().equals("普通用户");

        if (isUser) {
            String address = addressField.getText().trim();
            if (address.isEmpty()) {
                showError(errorLabel, "请填写居住地址");
                return;
            }
            if (address.length() > 255) {
                showError(errorLabel, "居住地址不能超过255个字符");
                return;
            }
            User user = new User(username, password, name, phone, address, age);
            if (!dataManager.addUser(user)) {
                showError(errorLabel, "注册失败，请检查数据库连接或用户名是否已存在");
                return;
            }
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
                if (age < 18 || age > 100) {
                    showError(errorLabel, "服务员工年龄必须为18-100岁");
                    return;
                }
                Employee employee = new Employee(username, password, name, age, phone, salary);
                if (!dataManager.addEmployee(employee)) {
                    showError(errorLabel, "注册失败，请检查数据库连接或用户名是否已存在");
                    return;
                }
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

    private void hideError(Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setVisible(false);
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("成功");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
