import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class EmployeeDashboardScene {
    private Employee employee;
    private ToggleButton workStatusButton;
    private Label assignedUserLabel;
    private Button cancelAssignmentButton;

    public EmployeeDashboardScene(Employee employee) {
        this.employee = employee;
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

        Label titleLabel = new Label("员工仪表板");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.DARKSLATEBLUE);

        HBox userInfo = new HBox(10);
        userInfo.setAlignment(Pos.CENTER_RIGHT);

        Label welcomeLabel = new Label("欢迎, " + employee.getName());
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

        // 员工信息卡片
        VBox employeeInfoCard = createEmployeeInfoCard();

        // 工作状态控制
        VBox workStatusCard = createWorkStatusCard();

        // 分配用户信息
        VBox assignedUserCard = createAssignedUserCard();

        content.getChildren().addAll(employeeInfoCard, workStatusCard, assignedUserCard);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f5f6fa; -fx-border: none; -fx-background-color: #f5f6fa;");

        return scrollPane;
    }

    private VBox createEmployeeInfoCard() {
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

        Label nameValue = new Label(employee.getName());
        nameValue.setTextFill(Color.DARKSLATEGRAY);
        nameValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        Label ageLabel = new Label("年龄:");
        ageLabel.setTextFill(Color.BLACK);
        ageLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label ageValue = new Label(String.valueOf(employee.getAge()));
        ageValue.setTextFill(Color.DARKSLATEGRAY);
        ageValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        Label phoneLabel = new Label("电话:");
        phoneLabel.setTextFill(Color.BLACK);
        phoneLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label phoneValue = new Label(employee.getPhone());
        phoneValue.setTextFill(Color.DARKSLATEGRAY);
        phoneValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        Label salaryLabel = new Label("薪资:");
        salaryLabel.setTextFill(Color.BLACK);
        salaryLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label salaryValue = new Label("¥" + String.format("%.2f", employee.getSalary()));
        salaryValue.setTextFill(Color.DARKSLATEGRAY);
        salaryValue.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

        infoGrid.add(nameLabel, 0, 0);
        infoGrid.add(nameValue, 1, 0);
        infoGrid.add(ageLabel, 0, 1);
        infoGrid.add(ageValue, 1, 1);
        infoGrid.add(phoneLabel, 0, 2);
        infoGrid.add(phoneValue, 1, 2);
        infoGrid.add(salaryLabel, 0, 3);
        infoGrid.add(salaryValue, 1, 3);

        card.getChildren().addAll(title, infoGrid);

        return card;
    }

    private VBox createWorkStatusCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        card.setMaxWidth(600);

        Label title = new Label("工作状态");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.DARKSLATEBLUE);

        workStatusButton = new ToggleButton();
        workStatusButton.setPrefSize(120, 40);
        updateWorkStatusButton();

        workStatusButton.setOnAction(e -> {
            boolean newStatus = !employee.isWorking();
            AppManager.getInstance().getDataManager().updateEmployeeStatus(employee.getUsername(), newStatus);
            employee.setWorking(newStatus);
            updateWorkStatusButton();
        });

        card.getChildren().addAll(title, workStatusButton);

        return card;
    }

    private VBox createAssignedUserCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        card.setMaxWidth(600);

        Label title = new Label("分配的用户");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.DARKSLATEBLUE);

        assignedUserLabel = new Label(getAssignedUserName());
        assignedUserLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        assignedUserLabel.setTextFill(Color.DARKSLATEGRAY);
        assignedUserLabel.setWrapText(true);

        // 按钮容器
        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);

        // 添加刷新按钮
        Button refreshButton = new Button("刷新信息");
        refreshButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        refreshButton.setOnAction(e -> {
            assignedUserLabel.setText(getAssignedUserName());
            updateCancelButtonVisibility();
        });

        // 添加解约按钮（员工端）
        cancelAssignmentButton = new Button("取消分配");
        cancelAssignmentButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
        cancelAssignmentButton.setOnAction(e -> cancelAssignment());
        updateCancelButtonVisibility();

        buttonContainer.getChildren().addAll(refreshButton, cancelAssignmentButton);

        card.getChildren().addAll(title, assignedUserLabel, buttonContainer);

        return card;
    }

    private void updateWorkStatusButton() {
        if (employee.isWorking()) {
            workStatusButton.setText("工作中");
            workStatusButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            workStatusButton.setText("暂停工作");
            workStatusButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    // 新增：员工端解约方法
    private void cancelAssignment() {
        if (employee.getAssignedUser() == null || employee.getAssignedUser().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("当前没有分配的用户");
            alert.showAndWait();
            return;
        }

        // 确认对话框
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认取消分配");
        confirmAlert.setHeaderText("您确定要取消当前用户的分配吗？");
        confirmAlert.setContentText("取消分配后该用户将需要重新选择员工。");

        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            DataManager dataManager = AppManager.getInstance().getDataManager();
            dataManager.cancelAssignment(employee.getAssignedUser());

            // 更新当前员工对象
            employee.setAssignedUser("");

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("取消分配成功");
            successAlert.setHeaderText(null);
            successAlert.setContentText("您已成功取消用户分配。");
            successAlert.showAndWait();

            // 刷新界面
            AppManager.getInstance().showEmployeeDashboard(employee);
        }
    }

    // 新增：更新解约按钮可见性
    private void updateCancelButtonVisibility() {
        boolean hasAssignment = employee.getAssignedUser() != null && !employee.getAssignedUser().isEmpty();
        cancelAssignmentButton.setVisible(hasAssignment);
        cancelAssignmentButton.setManaged(hasAssignment);
    }

    private String getAssignedUserName() {
        if (employee.getAssignedUser().isEmpty()) {
            return "当前没有分配的用户";
        }

        DataManager dataManager = AppManager.getInstance().getDataManager();
        for (User user : dataManager.getUsers()) {
            if (user.getUsername().equals(employee.getAssignedUser())) {
                return String.format("用户: %s\n电话: %s\n地址: %s",
                        user.getName(), user.getPhone(), user.getAddress());
            }
        }
        return "当前没有分配的用户";
    }
}