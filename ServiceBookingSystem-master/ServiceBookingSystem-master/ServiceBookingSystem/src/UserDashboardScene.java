import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class UserDashboardScene {
    private User user;
    private VBox employeeListContainer;
    private Label assignedEmployeeLabel;
    private Button cancelAssignmentButton;

    public UserDashboardScene(User user) {
        this.user = user;
    }

    public Scene createScene() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f5f6fa;");

        // 顶部栏
        HBox topBar = createTopBar();
        mainLayout.setTop(topBar);

        // 内容区域
        ScrollPane content = createContent();
        mainLayout.setCenter(content);

        return new Scene(mainLayout, 1000, 700);
    }

    private HBox createTopBar() {
        HBox topBar = new HBox();
        topBar.setPadding(new Insets(20));
        topBar.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("用户仪表板");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.DARKSLATEBLUE);

        HBox userInfo = new HBox(10);
        userInfo.setAlignment(Pos.CENTER_RIGHT);

        Label welcomeLabel = new Label("欢迎, " + user.getName());
        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        welcomeLabel.setTextFill(Color.BLACK);

        Button logoutButton = new Button("退出登录");
        logoutButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
        logoutButton.setOnAction(e -> AppManager.getInstance().showLoginScene());

        userInfo.getChildren().addAll(welcomeLabel, logoutButton);

        HBox.setHgrow(userInfo, Priority.ALWAYS);
        topBar.getChildren().addAll(titleLabel, userInfo);

        return topBar;
    }

    private ScrollPane createContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("-fx-background-color: #f5f6fa;");

        // 用户信息卡片
        VBox userInfoCard = createUserInfoCard();

        // 员工选择区域
        VBox employeeSelection = createEmployeeSelection();

        content.getChildren().addAll(userInfoCard, employeeSelection);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f5f6fa; -fx-border: none; -fx-background-color: #f5f6fa;");

        return scrollPane;
    }

    private VBox createUserInfoCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        card.setMaxWidth(600);

        Label title = new Label("个人信息");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.DARKSLATEBLUE);

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(20);
        infoGrid.setVgap(10);

        // 创建标签并确保文字颜色可见
        Label nameLabel = new Label("姓名:");
        nameLabel.setTextFill(Color.BLACK);
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label nameValue = new Label(user.getName());
        nameValue.setTextFill(Color.DARKSLATEGRAY);
        nameValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        Label ageLabel = new Label("年龄:");
        ageLabel.setTextFill(Color.BLACK);
        ageLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label ageValue = new Label(String.valueOf(user.getAge()));
        ageValue.setTextFill(Color.DARKSLATEGRAY);
        ageValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        Label phoneLabel = new Label("电话:");
        phoneLabel.setTextFill(Color.BLACK);
        phoneLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label phoneValue = new Label(user.getPhone());
        phoneValue.setTextFill(Color.DARKSLATEGRAY);
        phoneValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        Label addressLabel = new Label("地址:");
        addressLabel.setTextFill(Color.BLACK);
        addressLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label addressValue = new Label(user.getAddress());
        addressValue.setTextFill(Color.DARKSLATEGRAY);
        addressValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        addressValue.setWrapText(true);

        Label assignedLabel = new Label("分配员工:");
        assignedLabel.setTextFill(Color.BLACK);
        assignedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        assignedEmployeeLabel = new Label(getAssignedEmployeeName());
        assignedEmployeeLabel.setTextFill(Color.DARKSLATEGRAY);
        assignedEmployeeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        assignedEmployeeLabel.setWrapText(true);

        // 添加到网格
        infoGrid.add(nameLabel, 0, 0);
        infoGrid.add(nameValue, 1, 0);
        infoGrid.add(ageLabel, 0, 1);
        infoGrid.add(ageValue, 1, 1);
        infoGrid.add(phoneLabel, 0, 2);
        infoGrid.add(phoneValue, 1, 2);
        infoGrid.add(addressLabel, 0, 3);
        infoGrid.add(addressValue, 1, 3);
        infoGrid.add(assignedLabel, 0, 4);
        infoGrid.add(assignedEmployeeLabel, 1, 4);

        // 按钮容器
        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);

        // 添加刷新按钮
        Button refreshButton = new Button("刷新信息");
        refreshButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        refreshButton.setOnAction(e -> refreshUserInfo());

        // 添加解约按钮
        cancelAssignmentButton = new Button("解约");
        cancelAssignmentButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        cancelAssignmentButton.setOnAction(e -> cancelAssignment());
        updateCancelButtonVisibility();

        buttonContainer.getChildren().addAll(refreshButton, cancelAssignmentButton);

        card.getChildren().addAll(title, infoGrid, buttonContainer);

        return card;
    }

    private VBox createEmployeeSelection() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        container.setMaxWidth(600);

        Label title = new Label("选择服务员工");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.DARKSLATEBLUE);

        // 如果已经有分配的员工，显示提示信息
        if (user.getAssignedEmployee() != null && !user.getAssignedEmployee().isEmpty()) {
            Label assignedInfo = new Label("您已分配了员工，如需更改请先解约当前分配");
            assignedInfo.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14;");
            assignedInfo.setWrapText(true);
            container.getChildren().add(assignedInfo);
        }

        employeeListContainer = new VBox(10);
        updateEmployeeList();

        container.getChildren().addAll(title, employeeListContainer);

        return container;
    }

    private void updateEmployeeList() {
        employeeListContainer.getChildren().clear();

        // 如果用户已经有分配的员工，则不显示员工列表
        if (user.getAssignedEmployee() != null && !user.getAssignedEmployee().isEmpty()) {
            Label assignedLabel = new Label("您已分配员工: " + getAssignedEmployeeName());
            assignedLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 14;");
            assignedLabel.setWrapText(true);
            employeeListContainer.getChildren().add(assignedLabel);
            return;
        }

        DataManager dataManager = AppManager.getInstance().getDataManager();
        java.util.List<Employee> availableEmployees = dataManager.getAvailableEmployees();

        if (availableEmployees.isEmpty()) {
            Label noEmployeesLabel = new Label("当前没有可用的员工");
            noEmployeesLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-font-size: 14;");
            employeeListContainer.getChildren().add(noEmployeesLabel);
        } else {
            for (Employee employee : availableEmployees) {
                HBox employeeCard = createEmployeeCard(employee);
                employeeListContainer.getChildren().add(employeeCard);
            }
        }
    }

    private HBox createEmployeeCard(Employee employee) {
        HBox card = new HBox(15);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; -fx-border-color: #e9ecef; -fx-border-radius: 8;");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(5);

        Label nameLabel = new Label(employee.getName());
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        nameLabel.setTextFill(Color.BLACK);

        Label detailsLabel = new Label(String.format("年龄: %d | 电话: %s | 薪资: ¥%.2f",
                employee.getAge(), employee.getPhone(), employee.getSalary()));
        detailsLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14;");

        info.getChildren().addAll(nameLabel, detailsLabel);

        HBox.setHgrow(info, Priority.ALWAYS);

        Button selectButton = new Button("选择");
        selectButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        selectButton.setOnAction(e -> selectEmployee(employee));

        card.getChildren().addAll(info, selectButton);

        return card;
    }

    private void selectEmployee(Employee employee) {
        DataManager dataManager = AppManager.getInstance().getDataManager();
        dataManager.assignEmployeeToUser(employee.getUsername(), user.getUsername());

        // 获取更新后的用户对象
        User updatedUser = dataManager.getUpdatedUser(user.getUsername());
        if (updatedUser != null) {
            user = updatedUser;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("选择成功");
        alert.setHeaderText(null);
        alert.setContentText("您已成功选择员工: " + employee.getName() + "\n员工联系方式: " + employee.getPhone());
        alert.showAndWait();

        // 刷新界面显示最新信息
        AppManager.getInstance().showUserDashboard(user);
    }

    // 修复解约方法：使用最新的用户对象
    private void cancelAssignment() {
        // 确认对话框
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认解约");
        confirmAlert.setHeaderText("您确定要解约当前员工吗？");
        confirmAlert.setContentText("解约后您可以重新选择其他员工。");

        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            DataManager dataManager = AppManager.getInstance().getDataManager();
            dataManager.cancelAssignment(user.getUsername());

            // 获取更新后的用户对象
            User updatedUser = dataManager.getUpdatedUser(user.getUsername());
            if (updatedUser != null) {
                user = updatedUser;
            }

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("解约成功");
            successAlert.setHeaderText(null);
            successAlert.setContentText("您已成功解约当前员工。");
            successAlert.showAndWait();

            // 刷新界面
            AppManager.getInstance().showUserDashboard(user);
        }
    }

    // 新增：刷新用户信息方法
    private void refreshUserInfo() {
        DataManager dataManager = AppManager.getInstance().getDataManager();
        User updatedUser = dataManager.getUpdatedUser(user.getUsername());
        if (updatedUser != null) {
            user = updatedUser;
        }
        assignedEmployeeLabel.setText(getAssignedEmployeeName());
        updateEmployeeList();
        updateCancelButtonVisibility();
    }

    // 新增：更新解约按钮可见性
    private void updateCancelButtonVisibility() {
        boolean hasAssignment = user.getAssignedEmployee() != null && !user.getAssignedEmployee().isEmpty();
        cancelAssignmentButton.setVisible(hasAssignment);
        cancelAssignmentButton.setManaged(hasAssignment);
    }

    private String getAssignedEmployeeName() {
        if (user.getAssignedEmployee() == null || user.getAssignedEmployee().isEmpty()) {
            return "未分配";
        }

        DataManager dataManager = AppManager.getInstance().getDataManager();
        for (Employee emp : dataManager.getEmployees()) {
            if (emp.getUsername().equals(user.getAssignedEmployee())) {
                return emp.getName() + " (" + emp.getPhone() + ")";
            }
        }
        return "未分配";
    }
}