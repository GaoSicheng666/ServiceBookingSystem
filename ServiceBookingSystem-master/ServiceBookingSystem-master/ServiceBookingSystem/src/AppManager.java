import javafx.scene.Scene;
import javafx.stage.Stage;

public class AppManager {
    private static AppManager instance;
    private Stage primaryStage;
    private DataManager dataManager;

    private AppManager() {
        dataManager = new DataManager();
    }

    public static AppManager getInstance() {
        if (instance == null) {
            instance = new AppManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("老年人服务预约系统");
        stage.setResizable(false);
    }

    public void showLoginScene() {
        LoginScene loginScene = new LoginScene();
        primaryStage.setScene(loginScene.createScene());
        primaryStage.show();
    }

    public void showRegisterScene() {
        RegisterScene registerScene = new RegisterScene();
        primaryStage.setScene(registerScene.createScene());
    }

    public void showUserDashboard(User user) {
        // 每次显示仪表板时都获取最新的用户数据
        User updatedUser = dataManager.getUpdatedUser(user.getUsername());
        if (updatedUser != null) {
            user = updatedUser;
        }
        UserDashboardScene userDashboard = new UserDashboardScene(user);
        primaryStage.setScene(userDashboard.createScene());
    }

    public void showEmployeeDashboard(Employee employee) {
        // 每次显示仪表板时都获取最新的员工数据
        Employee updatedEmployee = dataManager.getUpdatedEmployee(employee.getUsername());
        if (updatedEmployee != null) {
            employee = updatedEmployee;
        }
        EmployeeDashboardScene employeeDashboard = new EmployeeDashboardScene(employee);
        primaryStage.setScene(employeeDashboard.createScene());
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}