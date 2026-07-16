/* ==================== 系统配置与运行状态 ==================== */

// 后端 REST API 的固定根地址。后续请求只需要传入 /auth/login 等相对路径。
const DEFAULT_API_BASE = "http://121.40.96.66:8080/api";

// 一天分为四个固定服务时段，护工排班和老人预约共用同一组代码与显示文案。
const TIME_PERIODS = [
  ["MORNING", "早间", "06:00-10:00"],
  ["NOON", "中午", "10:00-14:00"],
  ["AFTERNOON", "下午", "14:00-18:00"],
  ["EVENING", "晚上", "18:00-22:00"]
];

// 所有会持续增长的业务列表统一每页展示 5 条。
const LIST_PAGE_SIZE = 5;
const USER_APPOINTMENTS_PAGE_SIZE = LIST_PAGE_SIZE;
const USER_APPOINTMENTS_REFRESH_INTERVAL = 15000;
const ADMIN_APPOINTMENTS_PAGE_SIZE = LIST_PAGE_SIZE;
const EMPLOYEE_TASKS_PAGE_SIZE = LIST_PAGE_SIZE;
let userAppointmentsRefreshTimer = null;
let userAppointmentsRefreshing = false;

/*
 * 全局状态对象，是这个单页应用的数据中心。
 * token、role、name 根据“记住密码”选择从 localStorage 或 sessionStorage 恢复；
 * 其余字段记录当前选中的页面、筛选条件和预约步骤；data 保存后端返回的数据。
 */
const state = {
  apiBase: DEFAULT_API_BASE,
  token: localStorage.getItem("eldercare.token") || sessionStorage.getItem("eldercare.token") || "",
  role: localStorage.getItem("eldercare.role") || sessionStorage.getItem("eldercare.role") || "",
  name: localStorage.getItem("eldercare.name") || sessionStorage.getItem("eldercare.name") || "",
  rememberPassword: localStorage.getItem("eldercare.rememberPassword") === "true",
  rememberedUsername: localStorage.getItem("eldercare.rememberedUsername") || "",
  authMode: "login",
  activeAdminTab: "services",
  appointmentsFilter: "",
  adminAppointmentsPage: 1,
  adminServicesPage: 1,
  adminUsersPage: 1,
  adminEmployeesPage: 1,
  userAppointmentsPage: 1,
  userServicesPage: 1,
  availableEmployeesPage: 1,
  userView: "home",
  employeeView: "tasks",
  employeeTasksPage: 1,
  employeeProfileDirty: false,
  bookingStep: 1,
  selectedDate: today(),
  selectedTimePeriods: [],
  selectedServiceId: "",
  selectedEmployeeId: "",
  employeeQuizResult: null,
  pendingEmployeeAvatar: null,
  data: {}
};

/*
 * 缓存 index.html 中长期存在的节点，避免每次渲染都重复查询 DOM。
 * 动态生成的表单、卡片和按钮不放在这里，而是通过事件委托统一处理。
 */
const el = {
  app: document.getElementById("app"),
  authPanel: document.getElementById("authPanel"),
  sessionPanel: document.getElementById("sessionPanel"),
  viewEyebrow: document.getElementById("viewEyebrow"),
  viewTitle: document.getElementById("viewTitle"),
  viewActions: document.getElementById("viewActions"),
  content: document.getElementById("content"),
  toast: document.getElementById("toast")
};

/* ==================== 全局事件委托 ====================
 * 页面大部分内容由 innerHTML 动态生成，因此不能只在启动时给每个按钮单独绑定事件。
 * 这里把 submit、click、change 事件统一监听在 document 上，再通过 data-* 属性判断业务动作。
 */

// 统一处理登录、注册和管理员新增服务表单。
document.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.target;
  const action = form.dataset.submit;
  if (!action) return;
  // 注册表单提交前再完整校验一次，防止用户没有逐个聚焦字段就直接提交。
  if (action === "register" && !validateRegisterForm(form)) return;
  // 必须在禁用表单控件之前读取数据；被 disabled 的控件不会进入 FormData。
  const payload = formData(form);

  try {
    setBusy(form, true);
    if (action === "login") await login(payload);
    if (action === "register") await register(payload);
    if (action === "create-service") await createService(payload);
    if (action === "employee-quiz") await submitEmployeeQuiz(payload);
    if (action === "employee-schedule") await saveEmployeeAvailability(form);
    if (action === "employee-capabilities") await saveEmployeeCapabilities(form);
    if (action === "employee-profile") await saveEmployeeProfile(payload);
    if (action === "employee-cancel-reason") {
      await submitEmployeeCancellation(form.dataset.appointmentId, payload);
    }
  } catch (error) {
    notify(error.message, "error");
  } finally {
    setBusy(form, false);
  }
});

/*
 * 统一处理带 data-action 属性的按钮。
 * 每个分支只负责调用对应业务函数；try/catch 负责把错误显示成页面提示，
 * finally 则恢复按钮状态，防止网络请求期间重复点击。
 */
document.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;

  try {
    button.disabled = true;
    if (action === "switch-auth") {
      state.authMode = button.dataset.mode;
      renderAuth();
      return;
    }
    if (action === "toggle-password") {
      togglePasswordVisibility(button);
      return;
    }
    if (action === "logout") {
      if (!confirmDiscardEmployeeProfile()) return;
      logout();
      return;
    }
    if (action === "refresh") {
      if (!confirmDiscardEmployeeProfile()) return;
      if (state.role === "EMPLOYEE" && state.employeeView === "profile") {
        state.employeeProfileDirty = false;
        state.pendingEmployeeAvatar = null;
      }
      await refreshDashboard();
      return;
    }
    if (action === "load-available") {
      await loadAvailableEmployees();
      return;
    }
    if (action === "book") {
      await bookEmployee(button.dataset.employeeId);
      return;
    }
    if (action === "start-booking") {
      await startBooking();
      return;
    }
    if (action === "show-user-appointments") {
      await showUserAppointments();
      return;
    }
    if (action === "user-home") {
      await showUserHome();
      return;
    }
    if (action === "select-service") {
      selectBookingService(button.dataset.serviceId);
      return;
    }
    if (action === "select-date") {
      selectBookingDate(button.dataset.date);
      return;
    }
    if (action === "select-booking-period") {
      toggleBookingPeriod(button.dataset.period);
      return;
    }
    if (action === "use-custom-date") {
      await confirmBookingDate();
      return;
    }
    if (action === "choose-employee") {
      chooseBookingEmployee(button.dataset.employeeId);
      return;
    }
    if (action === "confirm-employee") {
      await confirmBookingEmployee();
      return;
    }
    if (action === "confirm-booking-time") {
      confirmBookingTime();
      return;
    }
    if (action === "confirm-booking") {
      await bookEmployee(state.selectedEmployeeId);
      return;
    }
    if (action === "booking-back") {
      bookingBack();
      return;
    }
    if (action === "user-appointment-filter") {
      await filterUserAppointments(button.dataset.status || "");
      return;
    }
    if (action === "user-appointments-page") {
      await changeUserAppointmentsPage(Number(button.dataset.page));
      return;
    }
    if (action === "user-services-page") {
      await changeUserServicesPage(Number(button.dataset.page));
      return;
    }
    if (action === "available-employees-page") {
      await changeAvailableEmployeesPage(Number(button.dataset.page));
      return;
    }
    if (action === "cancel-user") {
      await cancelUserAppointment(button.dataset.id);
      return;
    }
    if (action === "employee-status") {
      await updateEmployeeStatus(button.dataset.working === "true");
      return;
    }
    if (action === "complete-training") {
      await completeEmployeeTraining();
      return;
    }
    if (action === "employee-view") {
      await showEmployeeView(button.dataset.view);
      return;
    }
    if (action === "remove-employee-avatar") {
      removeEmployeeAvatarPreview();
      return;
    }
    if (action === "select-all-availability") {
      setAllEmployeeAvailability(button, true);
      return;
    }
    if (action === "clear-all-availability") {
      setAllEmployeeAvailability(button, false);
      return;
    }
    if (action === "employee-task-filter") {
      await filterEmployeeTasks(button.dataset.status || "");
      return;
    }
    if (action === "employee-tasks-page") {
      await changeEmployeeTasksPage(Number(button.dataset.page));
      return;
    }
    if (action === "employee-complete") {
      await employeeComplete(button.dataset.id);
      return;
    }
    if (action === "employee-cancel") {
      openEmployeeCancelDialog(button.dataset.id);
      return;
    }
    if (action === "close-cancel-dialog") {
      closeEmployeeCancelDialog();
      return;
    }
    if (action === "show-phone") {
      showContactPhone(button.dataset.phone, button.dataset.name);
      return;
    }
    if (action === "navigate") {
      openAmapNavigation(button.dataset.address);
      return;
    }
    if (action === "admin-tab") {
      state.activeAdminTab = button.dataset.tab;
      renderAdminDashboard();
      return;
    }
    if (action === "admin-appointments-page") {
      await changeAdminAppointmentsPage(Number(button.dataset.page));
      return;
    }
    if (action === "admin-list-page") {
      await changeAdminListPage(button.dataset.list, Number(button.dataset.page));
      return;
    }
    if (action === "delete-appointment") {
      await deleteAdminAppointment(button.dataset.id);
      return;
    }
    if (action === "save-service") {
      await saveService(button.dataset.id);
      return;
    }
    if (action === "delete-service") {
      await deleteService(button.dataset.id);
      return;
    }
    if (action === "toggle-user") {
      await toggleUser(button.dataset.id, button.dataset.active === "true");
      return;
    }
    if (action === "delete-user") {
      await deleteUser(button.dataset.id);
      return;
    }
    if (action === "toggle-employee") {
      await toggleEmployee(button.dataset.id, button.dataset.active === "true");
      return;
    }
    if (action === "grant-employee-training") {
      await grantEmployeeTraining(button.dataset.id);
      return;
    }
    if (action === "delete-employee") {
      await deleteEmployee(button.dataset.id);
    }
  } catch (error) {
    notify(error.message, "error");
  } finally {
    button.disabled = false;
  }
});

/*
 * 统一处理输入控件变化：护工接单状态、注册角色、日期和预约筛选等。
 * 护工状态切换失败时会把开关恢复为原值，保证界面与后端数据一致。
 */
document.addEventListener("change", async (event) => {
  const target = event.target;
  if (target.id === "employeeAvatarInput") {
    try {
      await previewEmployeeAvatar(target.files?.[0]);
    } catch (error) {
      target.value = "";
      notify(error.message, "error");
    }
  }
  if (target.id === "employeeWorkingToggle") {
    const requestedStatus = target.checked;
    target.disabled = true;
    try {
      const updated = await updateEmployeeStatus(requestedStatus);
      if (!updated) target.checked = !requestedStatus;
    } catch (error) {
      target.checked = !requestedStatus;
      notify(error.message, "error");
    } finally {
      target.disabled = false;
    }
  }
  if (target.id === "trainingAcknowledge") {
    const completeButton = document.getElementById("completeTrainingButton");
    if (completeButton) completeButton.disabled = !target.checked;
  }
  if (target.matches('form[data-submit="employee-schedule"] input[name="slot"]')) {
    const scheduleForm = target.closest("form");
    updateEmployeeAvailabilityCounter(scheduleForm);
  }
  if (target.matches('form[data-submit="employee-capabilities"] input[name="serviceId"]')) {
    updateEmployeeCapabilityCounter(target.closest("form"));
  }
  if (target.matches('form[data-submit="employee-cancel-reason"] input[name="cancelReason"]')) {
    const customField = target.form.querySelector("[data-custom-cancel-reason]");
    const customInput = customField?.querySelector("textarea");
    const usesCustomReason = target.value === "OTHER";
    if (customField) customField.hidden = !usesCustomReason;
    if (customInput) {
      customInput.required = usesCustomReason;
      if (usesCustomReason) customInput.focus();
      else customInput.value = "";
    }
  }
  if (target.id === "registerRole") renderRegisterRoleFields(target.value);
  if (target.id === "serviceSelect") state.selectedServiceId = target.value;
  if (target.id === "bookingDate") {
    if (state.selectedDate !== target.value) {
      state.selectedTimePeriods = [];
      state.selectedEmployeeId = "";
      state.availableEmployeesPage = 1;
      state.data.availableEmployees = [];
      state.data.selectedEmployee = null;
      state.data.selectedEmployeeAvailablePeriods = [];
    }
    state.selectedDate = target.value;
    renderUserDashboard();
  }
  if (target.id === "appointmentFilter") {
    state.appointmentsFilter = target.value;
    await refreshDashboard();
  }
  if (target.id === "adminStatusFilter") {
    state.appointmentsFilter = target.value;
    state.adminAppointmentsPage = 1;
    await loadAdminAppointmentsPage();
    renderAdminDashboard();
  }
});

/*
 * 注册输入提示使用 focusin 和 input 事件委托：
 * - 刚选中输入框时显示该字段的完整要求；
 * - 输入后根据当前内容显示还差多少或哪项格式不正确。
 */
document.addEventListener("focusin", (event) => {
  if (event.target.matches("[data-register-rule]")) {
    updateRegisterHint(event.target, true);
  }
});

document.addEventListener("input", (event) => {
  if (event.target.closest('form[data-submit="employee-profile"]')) {
    markEmployeeProfileDirty();
  }
  if (event.target.matches("[data-register-rule]")) {
    updateRegisterHint(event.target, true);
    // 原密码改变后立即重新检查确认密码，避免旧的绿色对号继续表示两次密码一致。
    if (event.target.name === "password") {
      const confirmInput = event.target.form?.querySelector('input[name="confirmPassword"]');
      const confirmHint = confirmInput?.closest(".field")?.querySelector("[data-register-hint]");
      if (confirmInput && (confirmInput.value || (confirmHint && !confirmHint.hidden))) {
        updateRegisterHint(confirmInput, true);
      }
    }
  }
});

// 浏览器关闭或刷新时使用原生离开确认，避免护工误丢失尚未保存的个人资料。
window.addEventListener("beforeunload", (event) => {
  if (!state.employeeProfileDirty) return;
  event.preventDefault();
  event.returnValue = "";
});

// 中文输入法选词期间会临时产生拼音字符，此时保持“输入尚未完成”，避免提前显示绿色对号。
document.addEventListener("compositionstart", (event) => {
  if (event.target.matches("[data-register-rule]")) {
    event.target.dataset.composing = "true";
    updateRegisterHint(event.target, true);
  }
});

document.addEventListener("compositionend", (event) => {
  if (event.target.matches("[data-register-rule]")) {
    delete event.target.dataset.composing;
    updateRegisterHint(event.target, true);
  }
});

/* ==================== 应用启动 ==================== */

// 先绘制当前页面；如果本地已有令牌，再向后端刷新一次真实数据。
render();
if (state.token) refreshDashboard().catch((error) => notify(error.message, "error"));

/**
 * 根据当前登录状态绘制页面固定部分。
 * 未登录时清空工作区，已登录时具体业务页面由 refreshDashboard 负责加载。
 */
function render() {
  syncLayout();
  renderAuth();
  renderSession();
  if (!state.token) {
    el.viewTitle.textContent = "登录后开始";
    el.viewActions.innerHTML = "";
    el.content.innerHTML = "";
  }
}

/**
 * 渲染登录或注册表单。
 * state.authMode 决定当前表单；管理员没有公开注册入口，只能由后端预置管理员账号。
 */
function renderAuth() {
  if (state.token) {
    el.authPanel.classList.add("hidden");
    return;
  }
  el.authPanel.classList.remove("hidden");
  if (state.authMode === "register") {
    el.authPanel.innerHTML = `
      <div class="auth-card-header">
        <p>新用户注册</p>
        <h2 id="auth-title">创建您的账号</h2>
      </div>
      <div class="tabs auth-tabs" role="tablist" aria-label="账号操作">
        <button class="tab" type="button" role="tab" aria-selected="false" data-action="switch-auth" data-mode="login">登录</button>
        <button class="tab active" type="button" role="tab" aria-selected="true" data-action="switch-auth" data-mode="register">注册</button>
      </div>
      <form data-submit="register">
        <label class="field">
          <span>角色</span>
          <select id="registerRole" name="role" required>
            <option value="USER">老人用户</option>
            <option value="EMPLOYEE">护工员工</option>
          </select>
        </label>
        <label class="field">
          <span>用户名</span>
          <div class="register-control">
            <input name="username" data-register-rule="username" minlength="2" maxlength="20" required autocomplete="username" aria-describedby="register-hint-username" placeholder="请输入用户名">
            ${registerHint("username")}
          </div>
        </label>
        <label class="field">
          <span>密码</span>
          <div class="register-control password-control">
            <input name="password" data-register-rule="password" type="password" minlength="6" maxlength="20" required autocomplete="new-password" aria-describedby="register-hint-password" placeholder="请输入密码">
            ${passwordEyeButton()}
            ${registerHint("password")}
          </div>
        </label>
        <label class="field">
          <span>确认密码</span>
          <div class="register-control password-control">
            <input name="confirmPassword" data-register-rule="confirmPassword" type="password" minlength="6" maxlength="20" required autocomplete="new-password" aria-describedby="register-hint-confirm-password" placeholder="请再次输入密码">
            ${passwordEyeButton()}
            ${registerHint("confirm-password")}
          </div>
        </label>
        <label class="field">
          <span>姓名</span>
          <div class="register-control">
            <input name="name" data-register-rule="name" minlength="2" maxlength="20" required aria-describedby="register-hint-name" placeholder="请输入真实姓名">
            ${registerHint("name")}
          </div>
        </label>
        <label class="field">
          <span>联系电话</span>
          <div class="register-control">
            <input name="phone" data-register-rule="phone" type="tel" minlength="6" maxlength="20" required inputmode="numeric" autocomplete="tel" aria-describedby="register-hint-phone" placeholder="请输入联系电话">
            ${registerHint("phone")}
          </div>
        </label>
        <label class="field">
          <span>年龄</span>
          <div class="register-control">
            <input name="age" data-register-rule="age" type="number" min="1" max="120" required inputmode="numeric" aria-describedby="register-hint-age" placeholder="请输入年龄">
            ${registerHint("age")}
          </div>
        </label>
        <div id="roleFields"></div>
        <button class="btn auth-submit" type="submit">完成注册</button>
      </form>
    `;
    renderRegisterRoleFields("USER");
    return;
  }

  el.authPanel.innerHTML = `
    <div class="auth-card-header">
      <p>欢迎回来</p>
      <h2 id="auth-title">登录您的账号</h2>
    </div>
    <div class="tabs auth-tabs" role="tablist" aria-label="账号操作">
      <button class="tab active" type="button" role="tab" aria-selected="true" data-action="switch-auth" data-mode="login">登录</button>
      <button class="tab" type="button" role="tab" aria-selected="false" data-action="switch-auth" data-mode="register">注册</button>
    </div>
    <form data-submit="login">
      <label class="field"><span>用户名</span><input name="username" value="${attr(state.rememberedUsername)}" required autocomplete="username" placeholder="请输入用户名"></label>
      <label class="field">
        <span>密码</span>
        <div class="password-control">
          <input name="password" type="password" required autocomplete="current-password" placeholder="请输入密码">
          ${passwordEyeButton()}
        </div>
      </label>
      <label class="remember-password"><input name="rememberPassword" type="checkbox" ${state.rememberPassword ? "checked" : ""}>记住密码</label>
      <button class="btn auth-submit" type="submit">立即登录</button>
    </form>
  `;
}

/**
 * 根据注册角色补充差异字段。
 * 老人用户填写居住地址；护工没有额外注册字段，但年龄下限调整为 18 岁。
 * @param {string} role 后端约定的角色值：USER 或 EMPLOYEE。
 */
function renderRegisterRoleFields(role) {
  const box = document.getElementById("roleFields");
  if (!box) return;
  const ageInput = box.closest("form")?.querySelector('input[name="age"]');
  if (ageInput) {
    ageInput.min = role === "EMPLOYEE" ? "18" : "1";
    ageInput.placeholder = role === "EMPLOYEE" ? "护工年龄需满18岁" : "请输入年龄";
  }
  if (role === "EMPLOYEE") {
    box.innerHTML = "";
  } else {
    box.innerHTML = `
      <label class="field">
        <span>居住地址</span>
        <div class="register-control">
          <textarea name="address" data-register-rule="address" maxlength="255" required aria-describedby="register-hint-address" placeholder="请输入详细居住地址"></textarea>
          ${registerHint("address")}
        </div>
      </label>`;
  }

  // 切换老人/护工角色后，年龄最低要求也会变化；若提示已经显示则立即同步内容。
  if (ageInput) updateRegisterHint(ageInput, false);
}

/** 为注册字段生成初始隐藏的提示容器，具体文字由 updateRegisterHint 动态写入。 */
function registerHint(name) {
  return `<small id="register-hint-${name}" class="register-hint" data-register-hint="${name}" aria-live="polite" hidden></small>`;
}

/**
 * 生成密码框右侧的小眼睛按钮。图形采用 Lucide Eye 图标的路径，按钮不参与表单提交。
 */
function passwordEyeButton() {
  return `
    <button class="password-eye" type="button" data-action="toggle-password" aria-label="显示密码" aria-pressed="false" title="显示密码">
      ${passwordEyeIcon(false)}
    </button>`;
}

/** 根据当前密码可见状态返回 Lucide Eye 或 Eye Off 图标。 */
function passwordEyeIcon(visible) {
  if (visible) {
    return `<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m2 2 20 20"/><path d="M6.71 6.71C4.23 8.1 2.73 10.17 2 12c1.73 4.39 5.73 7 10 7 1.42 0 2.79-.29 4.03-.84"/><path d="M10.73 5.08A10.8 10.8 0 0 1 12 5c4.27 0 8.27 2.61 10 7a11.6 11.6 0 0 1-2.2 3.19"/><path d="M14.12 14.12a3 3 0 0 1-4.24-4.24"/></svg>`;
  }
  return `<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.06 12.35a1 1 0 0 1 0-.7C3.79 7.26 7.73 5 12 5s8.21 2.26 9.94 6.65a1 1 0 0 1 0 .7C20.21 16.74 16.27 19 12 19S3.79 16.74 2.06 12.35Z"/><circle cx="12" cy="12" r="3"/></svg>`;
}

/** 切换当前按钮所属密码框的明文/密文状态，并同步图标和无障碍说明。 */
function togglePasswordVisibility(button) {
  const input = button.closest(".password-control")?.querySelector('input[type="password"], input[type="text"]');
  if (!input) return;
  const visible = input.type === "password";
  input.type = visible ? "text" : "password";
  button.innerHTML = passwordEyeIcon(visible);
  button.setAttribute("aria-pressed", String(visible));
  button.setAttribute("aria-label", visible ? "隐藏密码" : "显示密码");
  button.title = visible ? "隐藏密码" : "显示密码";
  input.focus();
}

/**
 * 根据字段类型和当前输入计算提示内容。
 * 未满足时返回一条可独立阅读的红色提示；valid=true 时界面只显示绿色对号。
 */
function registerFieldFeedback(input) {
  const rule = input.dataset.registerRule;
  const rawValue = input.value;
  const value = rawValue.trim();

  if (input.dataset.composing === "true") {
    return { message: "请先完成文字输入", valid: false };
  }

  if (rule === "username") {
    if (!value) return { message: "请输入2-20个字符，可使用中文、字母、数字和下划线", valid: false };
    if (value.length < 2) return { message: `还需输入${2 - value.length}个字符`, valid: false };
    if (!/^[\p{Script=Han}A-Za-z0-9_]+$/u.test(value)) return { message: "只能使用中文、字母、数字和下划线", valid: false };
    return { message: "已符合要求", valid: true };
  }

  if (rule === "password") {
    if (!rawValue) return { message: "请输入6-20个字符，并同时包含字母和数字", valid: false };
    if (rawValue.length < 6) return { message: `还需输入${6 - rawValue.length}个字符`, valid: false };
    const missing = [];
    if (!/[A-Za-z]/.test(rawValue)) missing.push("字母");
    if (!/\d/.test(rawValue)) missing.push("数字");
    if (missing.length) return { message: `还需包含${missing.join("和")}`, valid: false };
    return { message: "已符合要求", valid: true };
  }

  if (rule === "confirmPassword") {
    const password = input.form?.querySelector('input[name="password"]')?.value || "";
    if (!rawValue) return { message: "请再次输入密码", valid: false };
    if (rawValue !== password) return { message: "两次输入的密码不一致", valid: false };
    return { message: "已符合要求", valid: true };
  }

  if (rule === "name") {
    if (!value) return { message: "请输入2-20个汉字，可使用间隔号", valid: false };
    if (value.length < 2) return { message: `还需输入${2 - value.length}个汉字`, valid: false };
    if (!/^[\p{Script=Han}·]+$/u.test(value)) return { message: "请先把拼音转换为汉字，姓名只能使用汉字和间隔号", valid: false };
    return { message: "已符合要求", valid: true };
  }

  if (rule === "phone") {
    if (!value) return { message: "请输入6-20位数字", valid: false };
    if (!/^\d+$/.test(value)) return { message: "只能输入数字，不能包含空格或符号", valid: false };
    if (value.length < 6) return { message: `还需输入${6 - value.length}位数字`, valid: false };
    return { message: "已符合要求", valid: true };
  }

  if (rule === "age") {
    const role = document.getElementById("registerRole")?.value;
    const minimum = role === "EMPLOYEE" ? 18 : 1;
    const roleLabel = role === "EMPLOYEE" ? "护工" : "老人用户";
    if (!value) return { message: `请输入${roleLabel}年龄，范围为${minimum}-120岁`, valid: false };
    const age = Number(value);
    if (!Number.isInteger(age)) return { message: "年龄必须填写整数", valid: false };
    if (age < minimum) return { message: `年龄需达到${minimum}岁`, valid: false };
    if (age > 120) return { message: "年龄不能超过120岁", valid: false };
    return { message: "已符合要求", valid: true };
  }

  if (rule === "address") {
    if (!value) return { message: "请填写能够联系到您的详细居住地址", valid: false };
    if (value.length < 5) return { message: `还需补充${5 - value.length}个字符`, valid: false };
    if (!/^[\p{Script=Han}\d\s\-—（）()、，,.]+$/u.test(value)) return { message: "请先把拼音转换为汉字，可使用汉字、数字和常用地址符号", valid: false };
    return { message: "已符合要求", valid: true };
  }

  return { message: "请检查输入内容是否正确", valid: false };
}

/** 更新一个注册输入框下方的提示，并同步浏览器原生表单有效状态。 */
function updateRegisterHint(input, reveal = true) {
  const hint = input.closest(".field")?.querySelector("[data-register-hint]");
  if (!hint) return true;
  const feedback = registerFieldFeedback(input);
  if (reveal) hint.hidden = false;
  hint.classList.toggle("is-valid", feedback.valid);
  hint.innerHTML = feedback.valid
    ? `<span class="register-check" aria-hidden="true">✓</span><span class="visually-hidden">已符合要求</span>`
    : `<strong>${escapeHtml(feedback.message)}</strong>`;
  input.setCustomValidity(feedback.valid ? "" : feedback.message);
  return feedback.valid;
}

/** 校验当前注册表单中的所有可见字段，并把焦点移到第一项未满足要求的输入框。 */
function validateRegisterForm(form) {
  const inputs = Array.from(form.querySelectorAll("[data-register-rule]"));
  const valid = inputs.map((input) => updateRegisterHint(input, true));
  const firstInvalid = inputs.find((input, index) => !valid[index]);
  if (firstInvalid) {
    firstInvalid.focus();
    form.reportValidity();
    return false;
  }
  return true;
}

/**
 * 渲染已登录账号区域。
 * 老人端只保留返回首页与退出，护工和管理员额外提供刷新数据按钮。
 */
function renderSession() {
  syncLayout();
  if (!state.token) {
    el.sessionPanel.classList.remove("senior-session");
    el.sessionPanel.classList.add("hidden");
    el.sessionPanel.innerHTML = "";
    return;
  }
  el.authPanel.classList.add("hidden");
  el.sessionPanel.classList.remove("hidden");
  el.sessionPanel.classList.toggle("senior-session", state.role === "USER");
  if (state.role === "USER") {
    el.sessionPanel.innerHTML = `
      <h2 id="session-title">您好</h2>
      <p><strong>${escapeHtml(state.name || "已登录")}</strong></p>
      <div class="divider"></div>
      <button class="btn secondary" type="button" data-action="user-home">返回服务首页</button>
      <button class="btn ghost" type="button" data-action="logout">退出登录</button>
    `;
    return;
  }
  const employeeNavigation = state.role === "EMPLOYEE" && state.data.employee?.quizPassed
    ? renderEmployeeSidebarNavigation()
    : "";
  el.sessionPanel.innerHTML = `
    <h2 id="session-title">当前账号</h2>
    <p><strong>${escapeHtml(state.name || "已登录")}</strong></p>
    <p class="muted small">${roleName(state.role)}</p>
    ${employeeNavigation}
    <div class="divider"></div>
    <button class="btn secondary" type="button" data-action="refresh">刷新数据</button>
    <button class="btn ghost" type="button" data-action="logout">退出登录</button>
  `;
}

/**
 * 把 JavaScript 登录状态同步为根节点类名，供 CSS 切换三种布局。
 * is-authenticated 表示已登录，is-senior 表示当前是老人用户。
 */
function syncLayout() {
  el.app.classList.toggle("is-authenticated", Boolean(state.token));
  el.app.classList.toggle("is-senior", Boolean(state.token) && state.role === "USER");
}

/**
 * 调用登录接口并保存会话。
 * 勾选“记住密码”时将 JWT 会话写入 localStorage，否则只写入 sessionStorage；
 * 前端不会保存明文密码，登录状态的最长时间仍由后端 JWT 有效期决定。
 * @param {Object} body 登录表单中的 username 和 password。
 */
async function login(body) {
  const rememberPassword = body.rememberPassword === "on";
  const username = body.username;
  delete body.rememberPassword;
  const res = await api("/auth/login", { method: "POST", body });
  state.token = res.token;
  state.role = res.role;
  state.name = res.name;
  state.rememberPassword = rememberPassword;
  state.rememberedUsername = rememberPassword ? username : "";

  clearStoredSession();
  const storage = rememberPassword ? localStorage : sessionStorage;
  storage.setItem("eldercare.token", state.token);
  storage.setItem("eldercare.role", state.role);
  storage.setItem("eldercare.name", state.name);

  if (rememberPassword) {
    localStorage.setItem("eldercare.rememberPassword", "true");
    localStorage.setItem("eldercare.rememberedUsername", username);
  } else {
    localStorage.removeItem("eldercare.rememberPassword");
    localStorage.removeItem("eldercare.rememberedUsername");
  }
  renderSession();
  notify("登录成功", "success");
  await refreshDashboard();
}

/**
 * 注册老人或护工账号。
 * number 类型的表单值默认是字符串，因此提交前把年龄显式转换为数字。
 * @param {Object} body 注册表单收集到的用户资料。
 */
async function register(body) {
  // 确认密码只用于浏览器端比较，不属于后端注册 DTO，提交前必须移除。
  delete body.confirmPassword;
  body.age = Number(body.age);
  await api("/auth/register", { method: "POST", body, auth: false });
  notify("注册成功，请登录", "success");
  state.authMode = "login";
  renderAuth();
}

/**
 * 清除前端会话和临时业务状态，并返回登录页。
 * 这里只清理当前浏览器中的 JWT，不会删除后端账号、业务数据或浏览器密码管理器中的信息。
 */
function logout(message = "已退出登录") {
  stopUserAppointmentsAutoRefresh();
  state.token = "";
  state.role = "";
  state.name = "";
  state.data = {};
  state.appointmentsFilter = "";
  state.adminAppointmentsPage = 1;
  state.adminServicesPage = 1;
  state.adminUsersPage = 1;
  state.adminEmployeesPage = 1;
  state.userAppointmentsPage = 1;
  state.userServicesPage = 1;
  state.availableEmployeesPage = 1;
  state.userView = "home";
  state.employeeView = "tasks";
  state.employeeTasksPage = 1;
  state.employeeProfileDirty = false;
  state.pendingEmployeeAvatar = null;
  state.bookingStep = 1;
  state.selectedDate = "";
  state.selectedTimePeriods = [];
  state.selectedServiceId = "";
  state.selectedEmployeeId = "";
  clearStoredSession();
  render();
  notify(message, message === "已退出登录" ? "success" : "error");
}

/**
 * 同时清理持久会话和当前标签会话，避免切换“记住密码”设置后留下两份互相冲突的 JWT。
 */
function clearStoredSession() {
  ["eldercare.token", "eldercare.role", "eldercare.name"].forEach((key) => {
    localStorage.removeItem(key);
    sessionStorage.removeItem(key);
  });
}

/**
 * 当前角色工作台的统一刷新入口。
 * 先调用角色对应的数据加载函数，再更新会话区域和业务内容，避免三个角色重复刷新逻辑。
 */
async function refreshDashboard() {
  if (!state.token) {
    render();
    return;
  }
  if (state.role === "USER") await loadUserDashboard();
  if (state.role === "EMPLOYEE") await loadEmployeeDashboard();
  if (state.role === "ADMIN") await loadAdminData();
  renderSession();
  if (state.role === "USER") renderUserDashboard();
  if (state.role === "EMPLOYEE") renderEmployeeDashboard();
  if (state.role === "ADMIN") renderAdminDashboard();
}

/* ==================== 老人用户端 ====================
 * 老人端只展示完成预约所必需的信息，并把预约拆成“服务、日期、人员、时段、确认”五步。
 */

/** 同时加载老人资料、当前服务页和本人的预约记录，并写入 state.data。 */
async function loadUserDashboard() {
  const [me, servicePage, appointmentPage] = await Promise.all([
    api("/users/me"),
    api(userServicesPageUrl()),
    api(userAppointmentsPageUrl(state.userView === "home"))
  ]);
  state.data.user = me;
  applyUserServicePage(servicePage);
  applyUserAppointmentPage(appointmentPage);
}

function userServicesPageUrl() {
  const params = new URLSearchParams({
    page: String(state.userServicesPage),
    size: String(LIST_PAGE_SIZE)
  });
  return `/services/page?${params.toString()}`;
}

function applyUserServicePage(pageData) {
  const normalized = normalizePageData(pageData, LIST_PAGE_SIZE);
  state.data.userServicePage = normalized;
  state.data.services = normalized.items;
  state.userServicesPage = normalized.page;
}

async function loadUserServicePage() {
  applyUserServicePage(await api(userServicesPageUrl()));
}

/**
 * 只刷新老人本人的预约记录，避免 15 秒定时任务反复读取个人资料和服务项目。
 * 手动刷新失败时把错误交给统一提示处理；自动刷新失败则保留当前列表，等待下次重试。
 */
async function refreshUserAppointments() {
  if (userAppointmentsRefreshing || !state.token || state.role !== "USER" || state.userView !== "appointments") return;
  userAppointmentsRefreshing = true;
  try {
    applyUserAppointmentPage(await api(userAppointmentsPageUrl(false)));
    renderUserDashboard();
  } catch (_) {
    // 静默刷新失败时保留当前页面，等待下一个刷新周期。
  } finally {
    userAppointmentsRefreshing = false;
  }
}

/** 老人首页只查询待服务数量；预约列表按当前状态和页码查询 5 条。 */
function userAppointmentsPageUrl(homeSummary = false) {
  const params = new URLSearchParams({
    page: String(homeSummary ? 1 : state.userAppointmentsPage),
    size: String(homeSummary ? 1 : USER_APPOINTMENTS_PAGE_SIZE)
  });
  const status = homeSummary ? "PENDING" : state.appointmentsFilter;
  if (status) params.set("status", status);
  return `/appointments/me/page?${params.toString()}`;
}

/** 保存后端分页响应，并同步后端校正后的有效页码。 */
function applyUserAppointmentPage(pageData) {
  const normalized = pageData && !Array.isArray(pageData)
    ? pageData
    : { items: Array.isArray(pageData) ? pageData : [], page: 1, size: USER_APPOINTMENTS_PAGE_SIZE, total: 0, totalPages: 1 };
  state.data.userAppointmentPage = normalized;
  state.data.userAppointments = normalized.items || [];
  state.userAppointmentsPage = Number(normalized.page || 1);
}

/** 进入“我的预约”后启动唯一的自动刷新定时器。 */
function startUserAppointmentsAutoRefresh() {
  if (userAppointmentsRefreshTimer || !state.token || state.role !== "USER" || state.userView !== "appointments") return;
  userAppointmentsRefreshTimer = window.setInterval(() => {
    if (document.hidden) return;
    if (!state.token || state.role !== "USER" || state.userView !== "appointments") {
      stopUserAppointmentsAutoRefresh();
      return;
    }
    refreshUserAppointments();
  }, USER_APPOINTMENTS_REFRESH_INTERVAL);
}

/** 离开预约记录页或退出登录时释放定时器。 */
function stopUserAppointmentsAutoRefresh() {
  if (!userAppointmentsRefreshTimer) return;
  window.clearInterval(userAppointmentsRefreshTimer);
  userAppointmentsRefreshTimer = null;
}

/**
 * 根据已选日期查询当天至少还有一个空闲时段的护工。
 * @param {boolean} showMessage 是否在查询完成后显示成功提示。
 */
async function loadAvailableEmployees(showMessage = true) {
  const date = state.selectedDate || today();
  if (!state.selectedServiceId) throw new Error("请先选择服务项目");
  const params = new URLSearchParams({
    date,
    serviceId: String(state.selectedServiceId),
    page: String(state.availableEmployeesPage),
    size: String(LIST_PAGE_SIZE)
  });
  const pageData = normalizePageData(
    await api(`/employees/available/page?${params.toString()}`),
    LIST_PAGE_SIZE
  );
  state.data.availableEmployeePage = pageData;
  state.data.availableEmployees = pageData.items;
  state.availableEmployeesPage = pageData.page;
  if (showMessage) notify("当天可预约的服务人员已更新", "success");
}

/** 选定护工后读取其在指定日期仍可预约的具体时段。 */
async function loadSelectedEmployeeAvailablePeriods() {
  if (!state.selectedEmployeeId) throw new Error("请先选择服务人员");
  if (!state.selectedDate) throw new Error("请先选择服务日期");
  const params = new URLSearchParams({ date: state.selectedDate });
  const periods = await api(`/employees/${Number(state.selectedEmployeeId)}/available-time-periods?${params.toString()}`);
  state.data.selectedEmployeeAvailablePeriods = Array.isArray(periods) ? periods : [];
}

/**
 * 老人端总渲染入口。
 * 根据 userView 在服务首页、五步预约和预约记录之间切换，不会同时堆叠多个流程。
 */
function renderUserDashboard() {
  const user = state.data.user || {};
  const services = state.data.services || [];
  const employees = state.data.availableEmployees || [];
  const appointments = state.data.userAppointments || [];
  el.viewEyebrow.textContent = "长辈服务首页";
  el.viewTitle.textContent = `您好，${user.name || state.name || "欢迎使用"}`;
  el.viewActions.innerHTML = "";

  if (state.userView !== "appointments") stopUserAppointmentsAutoRefresh();

  if (state.userView === "booking") {
    el.content.innerHTML = renderBookingWizard(services, employees);
    return;
  }
  if (state.userView === "appointments") {
    el.content.innerHTML = renderUserAppointments(appointments);
    startUserAppointmentsAutoRefresh();
    return;
  }

  const pendingCount = Number(state.data.userAppointmentPage?.total || 0);
  el.content.innerHTML = `
    <div class="senior-shell senior-home">
      <section class="senior-greeting">
        <p>今天需要什么帮助？</p>
        <h3>请选择您要办理的事情</h3>
      </section>
      <div class="senior-entry-list">
        <button class="senior-entry" type="button" data-action="start-booking">
          <span class="senior-entry-symbol" aria-hidden="true">＋</span>
          <span><strong>立即预约服务</strong><small>选择服务、日期、时间和服务人员</small></span>
          <span class="senior-entry-arrow" aria-hidden="true">›</span>
        </button>
        <button class="senior-entry" type="button" data-action="show-user-appointments">
          <span class="senior-entry-symbol" aria-hidden="true">日</span>
          <span><strong>查看我的预约</strong><small>${pendingCount ? `您有 ${pendingCount} 个待服务预约` : "查看预约记录和服务状态"}</small></span>
          <span class="senior-entry-arrow" aria-hidden="true">›</span>
        </button>
      </div>
    </div>
  `;
}

/** 进入新的预约流程，并清空上一次尚未提交的服务、时段和护工选择。 */
async function startBooking() {
  state.userView = "booking";
  state.bookingStep = 1;
  state.userServicesPage = 1;
  state.availableEmployeesPage = 1;
  state.selectedServiceId = "";
  state.selectedDate = "";
  state.selectedTimePeriods = [];
  state.selectedEmployeeId = "";
  state.data.availableEmployees = [];
  state.data.selectedService = null;
  state.data.selectedEmployee = null;
  state.data.selectedEmployeeAvailablePeriods = [];
  await loadUserServicePage();
  renderUserDashboard();
}

/** 返回老人服务首页，同时取消状态筛选并重新读取最新数据。 */
async function showUserHome() {
  state.userView = "home";
  state.appointmentsFilter = "";
  state.userAppointmentsPage = 1;
  await loadUserDashboard();
  renderSession();
  renderUserDashboard();
}

/** 打开“我的预约”，默认显示全部状态的预约记录。 */
async function showUserAppointments() {
  state.userView = "appointments";
  state.appointmentsFilter = "";
  state.userAppointmentsPage = 1;
  await loadUserDashboard();
  renderUserDashboard();
}

/**
 * 按状态重新查询老人预约。
 * @param {string} status 空字符串、PENDING、COMPLETED 或 CANCELLED。
 */
async function filterUserAppointments(status) {
  state.appointmentsFilter = status;
  state.userAppointmentsPage = 1;
  await loadUserDashboard();
  renderUserDashboard();
}

/** 保存选中的服务项目并进入日期选择步骤。 */
function selectBookingService(serviceId) {
  state.data.selectedService = (state.data.services || [])
    .find((service) => String(service.id) === String(serviceId)) || null;
  state.selectedServiceId = serviceId;
  state.selectedDate = "";
  state.selectedTimePeriods = [];
  state.selectedEmployeeId = "";
  state.availableEmployeesPage = 1;
  state.data.availableEmployees = [];
  state.data.selectedEmployee = null;
  state.data.selectedEmployeeAvailablePeriods = [];
  state.bookingStep = 2;
  renderUserDashboard();
}

/** 保存当前日期并留在第二步，等待用户确认后查询当天可预约护工。 */
function selectBookingDate(date) {
  if (!date) throw new Error("请选择服务日期");
  if (state.selectedDate !== date) {
    state.selectedTimePeriods = [];
    state.selectedEmployeeId = "";
    state.availableEmployeesPage = 1;
    state.data.availableEmployees = [];
    state.data.selectedEmployee = null;
    state.data.selectedEmployeeAvailablePeriods = [];
  }
  state.selectedDate = date;
  renderUserDashboard();
}

/** 在第四步选中或取消护工仍然可服务的一个时段。 */
function toggleBookingPeriod(period) {
  if (!TIME_PERIODS.some(([value]) => value === period)) {
    throw new Error("服务时段无效");
  }
  const availablePeriods = state.data.selectedEmployeeAvailablePeriods || [];
  if (!availablePeriods.includes(period)) {
    throw new Error("该服务人员在这个时段不可预约");
  }
  const selected = new Set(state.selectedTimePeriods);
  if (selected.has(period)) selected.delete(period);
  else selected.add(period);
  state.selectedTimePeriods = TIME_PERIODS
    .map(([value]) => value)
    .filter((value) => selected.has(value));
  renderUserDashboard();
}

/**
 * 确认第二步选中的日期，查询当天仍有空闲时段的护工后再进入第三步。
 * 选择与确认分开，避免老人误触日期后立即离开当前页面。
 */
async function confirmBookingDate() {
  if (!state.selectedDate) throw new Error("请先选择服务日期");
  if (state.selectedDate < today()) throw new Error("不能选择过去的日期");
  state.bookingStep = 3;
  state.selectedTimePeriods = [];
  state.selectedEmployeeId = "";
  state.availableEmployeesPage = 1;
  state.data.selectedEmployee = null;
  state.data.selectedEmployeeAvailablePeriods = [];
  await loadAvailableEmployees(false);
  renderUserDashboard();
}

/** 保存并展开当前选中的护工资料，继续停留在第三步供用户查看。 */
function chooseBookingEmployee(employeeId) {
  // 再次点击当前护工（包括右侧向上箭头）时收起资料并取消当前选择。
  const deselect = String(state.selectedEmployeeId) === String(employeeId);
  state.selectedEmployeeId = deselect ? "" : employeeId;
  state.data.selectedEmployee = deselect
    ? null
    : (state.data.availableEmployees || [])
      .find((employee) => String(employee.id) === String(employeeId)) || null;
  state.selectedTimePeriods = [];
  state.data.selectedEmployeeAvailablePeriods = [];
  renderUserDashboard();
}

/** 确认第三步选择的护工，读取其剩余时段后进入第四步。 */
async function confirmBookingEmployee() {
  if (!state.selectedEmployeeId) throw new Error("请先选择服务人员");
  state.selectedTimePeriods = [];
  await loadSelectedEmployeeAvailablePeriods();
  if (!state.data.selectedEmployeeAvailablePeriods.length) {
    throw new Error("该服务人员当天已经没有可预约时段，请返回选择其他人员");
  }
  state.bookingStep = 4;
  renderUserDashboard();
}

/** 确认第四步选择的一个或多个服务时段，再进入最终确认页。 */
function confirmBookingTime() {
  if (!state.selectedTimePeriods.length) throw new Error("请至少选择一个服务时段");
  state.bookingStep = 5;
  renderUserDashboard();
}

/** 返回预约上一步；已在第一步时直接退出预约流程并回到服务首页。 */
function bookingBack() {
  if (state.bookingStep <= 1) {
    state.userView = "home";
  } else {
    state.bookingStep -= 1;
  }
  renderUserDashboard();
}

/**
 * 组合五步预约页面的公共标题、进度条和当前步骤内容。
 * @returns {string} 可写入 content 容器的 HTML 字符串。
 */
function renderBookingWizard(services, employees) {
  const step = state.bookingStep;
  const headings = ["您需要什么服务？", "您希望哪天服务？", "选择服务人员", "选择服务时间", "确认预约信息"];
  let body = "";

  if (step === 1) body = renderServiceChoices(services);
  if (step === 2) body = renderDateChoices();
  if (step === 3) body = renderStaffChoices(employees);
  if (step === 4) body = renderEmployeeTimeChoices(services, employees);
  if (step === 5) body = renderBookingConfirmation(services, employees);

  return `
    <div class="senior-shell booking-wizard">
      <div class="booking-toolbar">
        <button class="senior-back" type="button" data-action="booking-back">← 返回上一步</button>
        <span>第 ${step} 步，共 5 步</span>
      </div>
      ${renderBookingProgress(step)}
      <header class="booking-heading"><h3>${headings[step - 1]}</h3></header>
      ${body}
    </div>
  `;
}

/** 根据当前步骤生成五段进度指示，已完成和当前步骤使用 active 类名。 */
function renderBookingProgress(step) {
  const labels = ["选服务", "选日期", "选人员", "选时间", "确认"];
  return `<div class="booking-progress" aria-label="预约进度">${labels.map((label, index) => `
    <div class="${index + 1 <= step ? "active" : ""}"><span>${index + 1}</span><small>${label}</small></div>
  `).join("")}</div>`;
}

/** 把后端返回的服务项目转成适合老人直接点击的大按钮。 */
function renderServiceChoices(services) {
  if (!services.length) return `<div class="senior-empty">暂时没有可预约的服务，请稍后再试。</div>`;
  const pageData = state.data.userServicePage || normalizePageData(services, LIST_PAGE_SIZE);
  return `
    <div class="list-result-meta">共 ${Number(pageData.total || services.length)} 项服务，每页 ${Number(pageData.size || LIST_PAGE_SIZE)} 项</div>
    <div class="service-choice-grid">${services.map((service) => `
      <button class="service-choice" type="button" data-action="select-service" data-service-id="${service.id}">
        <strong>${escapeHtml(service.name)}</strong>
        <span>${escapeHtml(service.description || "上门养老服务")}</span>
        <small>${Number(service.referencePrice) > 0 ? `参考价格 ¥${escapeHtml(service.referencePrice)}` : "价格以实际服务为准"}</small>
      </button>
    `).join("")}</div>
    ${renderPagination(pageData, "user-services-page", "服务项目分页")}`;
}

/** 提供今天、明天、后天和其他日期选择；日期确认后才查询当天可用护工。 */
function renderDateChoices() {
  const dates = [0, 1, 2].map((offset) => offsetDate(offset));
  const names = ["今天", "明天", "后天"];
  return `
    <div class="date-choice-grid">
      ${dates.map((date, index) => `
        <button class="date-choice ${state.selectedDate === date ? "selected" : ""}" type="button" data-action="select-date" data-date="${date}" aria-pressed="${state.selectedDate === date}">
          <strong>${names[index]}</strong><span>${formatFriendlyDate(date)}</span>
        </button>`).join("")}
    </div>
    <div class="custom-date-choice">
      <label class="field"><span>选择其他日期</span><input id="bookingDate" type="date" min="${today()}" value="${escapeHtml(state.selectedDate)}" required aria-label="选择其他服务日期"></label>
    </div>
    <div class="booking-step-action">
      <button class="btn senior-primary date-next-button" type="button" data-action="use-custom-date" ${state.selectedDate ? "" : "disabled"}>下一步：查看当天可预约人员</button>
    </div>
  `;
}

/**
 * 渲染指定日期的可预约护工列表。
 * 老人端只展示预约所需信息，不显示护工薪资等内部字段。
 */
function renderStaffChoices(employees) {
  if (!employees.length) return `
    <div class="senior-empty">
      <strong>${formatFriendlyDate(state.selectedDate)} 暂无可预约人员</strong>
      <p>请返回上一步选择其他日期。</p>
    </div>
  `;
  const pageData = state.data.availableEmployeePage || normalizePageData(employees, LIST_PAGE_SIZE);
  const selectedEmployee = state.data.selectedEmployee ||
    employees.find((employee) => String(employee.id) === String(state.selectedEmployeeId));
  return `
    <div class="list-result-meta">当天共有 ${Number(pageData.total || employees.length)} 名可预约人员，每页 ${Number(pageData.size || LIST_PAGE_SIZE)} 名</div>
    <div class="staff-choice-list">${employees.map((employee) => {
      const selected = String(employee.id) === String(state.selectedEmployeeId);
      return `
        <article class="staff-choice-card ${selected ? "selected" : ""}">
          <button class="staff-choice" type="button" data-action="choose-employee" data-employee-id="${employee.id}" aria-expanded="${selected}" aria-pressed="${selected}">
            <span class="staff-avatar">${renderEmployeeAvatarContent(employee)}</span>
            <span class="staff-choice-copy"><strong>${escapeHtml(employee.name)}</strong><small>${escapeHtml(employee.specialty || "平台服务人员")} · 当天有空闲时段</small></span>
            <span class="staff-expand" aria-hidden="true">⌄</span>
          </button>
          ${selected ? renderStaffBasicInfo(employee) : ""}
        </article>`;
    }).join("")}</div>
    ${renderPagination(pageData, "available-employees-page", "可预约服务人员分页")}
    <div class="staff-confirm-bar">
      <p>${selectedEmployee ? `已选择：<strong>${escapeHtml(selectedEmployee.name)}</strong>` : "请点击一位服务人员查看基本信息"}</p>
      <button class="btn senior-primary staff-next-button" type="button" data-action="confirm-employee" ${selectedEmployee ? "" : "disabled"}>下一步：选择服务时间</button>
    </div>`;
}

/**
 * 生成老人预约前可以查看的护工基本资料。
 * 联系电话和薪资不在选择阶段公开，电话会在预约成功后的预约卡片中显示。
 */
function renderStaffBasicInfo(employee) {
  const age = Number(employee.age) > 0 ? `${escapeHtml(employee.age)}岁` : "暂未提供";
  return `
    <div class="staff-basic-info">
      <dl>
        <div><dt>姓名</dt><dd>${escapeHtml(employee.name)}</dd></div>
        <div><dt>年龄</dt><dd>${age}</dd></div>
        <div><dt>接单状态</dt><dd>正在接单</dd></div>
        <div><dt>可预约日期</dt><dd>${formatFriendlyDate(state.selectedDate)}</dd></div>
        <div class="staff-info-wide"><dt>可预约时间</dt><dd>选定后在下一步查看具体时段</dd></div>
        <div class="staff-info-wide"><dt>擅长服务</dt><dd>${escapeHtml(employee.specialty || "暂未填写")}</dd></div>
        <div class="staff-info-wide"><dt>从业经历</dt><dd>${escapeHtml(employee.experience || "暂未填写")}</dd></div>
      </dl>
      ${employee.bio ? `<p class="staff-bio">${escapeHtml(employee.bio)}</p>` : ""}
      <p>联系电话将在预约成功后显示</p>
    </div>`;
}

/** 选定护工后展示其在所选日期的剩余时段，并允许老人多选。 */
function renderEmployeeTimeChoices(services, employees) {
  const service = state.data.selectedService || services.find((item) => String(item.id) === String(state.selectedServiceId));
  const employee = state.data.selectedEmployee || employees.find((item) => String(item.id) === String(state.selectedEmployeeId));
  if (!service || !employee) return `<div class="senior-empty">服务人员信息不完整，请返回上一步重新选择。</div>`;
  const availablePeriods = state.data.selectedEmployeeAvailablePeriods || [];
  const availableSet = new Set(availablePeriods);
  const slotCount = state.selectedTimePeriods.length;
  const totalAmount = calculateBookingTotal(service, slotCount);
  return `
    <section class="selected-staff-time-context" aria-label="当前选择">
      <span class="staff-avatar">${renderEmployeeAvatarContent(employee)}</span>
      <span><small>已选择服务人员</small><strong>${escapeHtml(employee.name)}</strong><em>${formatFriendlyDate(state.selectedDate)}</em></span>
    </section>
    <section class="booking-time-section employee-time-section" aria-labelledby="booking-time-title">
      <div class="booking-time-heading">
        <h4 id="booking-time-title">选择可服务时段</h4>
        <span>可以选择一个或多个时段</span>
      </div>
      <div class="booking-time-grid">
        ${TIME_PERIODS.map(([value, label, time]) => {
          const available = availableSet.has(value);
          const selected = state.selectedTimePeriods.includes(value);
          return `<button class="booking-time-choice ${selected ? "selected" : ""} ${available ? "" : "unavailable"}" type="button" data-action="select-booking-period" data-period="${value}" aria-pressed="${selected}" ${available ? "" : "disabled"}>
            <strong>${label}</strong><span>${time}</span><small>${available ? "可以预约" : "不可预约"}</small><b aria-hidden="true">✓</b>
          </button>`;
        }).join("")}
      </div>
      <div class="booking-time-summary">
        <p>${slotCount
          ? `已选择 <strong>${slotCount}</strong> 个时段：${formatTimePeriods(state.selectedTimePeriods)}`
          : `该护工当天有 <strong>${availablePeriods.length}</strong> 个可预约时段，请至少选择一个`}</p>
        <p class="booking-price">预计费用：<strong>¥${formatMoney(totalAmount)}</strong><small>¥${formatMoney(service.referencePrice)} × ${slotCount} 个时段</small></p>
      </div>
    </section>
    <div class="booking-step-action">
      <button class="btn senior-primary date-next-button" type="button" data-action="confirm-booking-time" ${slotCount ? "" : "disabled"}>下一步：确认预约</button>
    </div>
  `;
}

/**
 * 使用状态中保存的 ID 查找服务和护工，生成提交前的预约摘要。
 * 若其中任何一项不存在，则阻止提交并提示用户返回重新选择。
 */
function renderBookingConfirmation(services, employees) {
  const service = state.data.selectedService || services.find((item) => String(item.id) === String(state.selectedServiceId));
  const employee = state.data.selectedEmployee || employees.find((item) => String(item.id) === String(state.selectedEmployeeId));
  if (!service || !employee) return `<div class="senior-empty">预约信息不完整，请返回上一步重新选择。</div>`;
  const slotCount = state.selectedTimePeriods.length;
  const totalAmount = calculateBookingTotal(service, slotCount);
  return `
    <div class="booking-summary">
      <dl>
        <div><dt>服务项目</dt><dd>${escapeHtml(service.name)}</dd></div>
        <div><dt>服务日期</dt><dd>${formatFriendlyDate(state.selectedDate)}</dd></div>
        <div><dt>服务时间</dt><dd>${formatTimePeriods(state.selectedTimePeriods)}</dd></div>
        <div><dt>服务人员</dt><dd>${escapeHtml(employee.name)}</dd></div>
        <div><dt>费用计算</dt><dd>¥${formatMoney(service.referencePrice)} × ${slotCount} 个时段</dd></div>
        <div class="booking-summary-total"><dt>预约总额</dt><dd>¥${formatMoney(totalAmount)}</dd></div>
      </dl>
      <button class="btn senior-primary confirm-booking" type="button" data-action="confirm-booking">确认预约</button>
    </div>
  `;
}

/**
 * 生成预约状态筛选栏和纵向预约卡片列表。
 * 后端按最新记录优先返回当前页，每页固定 5 条。
 */
function renderUserAppointments(appointments) {
  const filters = [["", "全部"], ["PENDING", "待服务"], ["COMPLETED", "已完成"], ["CANCELLED", "已取消"]];
  const pageData = state.data.userAppointmentPage || {
    page: 1,
    size: USER_APPOINTMENTS_PAGE_SIZE,
    total: appointments.length,
    totalPages: 1
  };
  return `
    <div class="senior-shell senior-appointments">
      <div class="appointments-heading">
        <div class="appointments-title-block">
          <button class="senior-back" type="button" data-action="user-home">← 返回服务首页</button>
          <h3>我的预约</h3>
        </div>
      </div>
      <div class="senior-filter" aria-label="预约状态筛选">
        ${filters.map(([value, label]) => `<button type="button" class="${state.appointmentsFilter === value ? "active" : ""}" data-action="user-appointment-filter" data-status="${value}">${label}</button>`).join("")}
      </div>
      ${Number(pageData.total || 0) ? `
        <div class="appointments-result-meta">
          <span>共 ${Number(pageData.total || 0)} 条记录，每页 ${Number(pageData.size || USER_APPOINTMENTS_PAGE_SIZE)} 条</span>
          <strong>最新预约排在最前</strong>
        </div>
        <div class="appointment-card-list">${appointments.map(renderUserAppointmentCard).join("")}</div>
        ${renderUserAppointmentsPagination(pageData)}
      ` : `<div class="senior-empty">这里暂时没有预约记录。</div>`}
    </div>
  `;
}

/** 预约记录超过 5 条时显示上一页、页码和下一页。 */
function renderUserAppointmentsPagination(pageData) {
  const currentPage = Number(pageData.page || 1);
  const totalPages = Math.max(1, Number(pageData.totalPages || 1));
  if (totalPages <= 1) return "";
  return `
    <nav class="appointments-pagination" aria-label="预约记录分页">
      <button type="button" data-action="user-appointments-page" data-page="${currentPage - 1}" ${currentPage <= 1 ? "disabled" : ""}>← 上一页</button>
      <span>第 <strong>${currentPage}</strong> 页，共 ${totalPages} 页</span>
      <button type="button" data-action="user-appointments-page" data-page="${currentPage + 1}" ${currentPage >= totalPages ? "disabled" : ""}>下一页 →</button>
    </nav>
  `;
}

/** 切换老人预约流程中的服务项目页。 */
async function changeUserServicesPage(page) {
  if (!Number.isInteger(page) || page < 1) return;
  state.userServicesPage = page;
  state.selectedServiceId = "";
  state.data.selectedService = null;
  await loadUserServicePage();
  renderUserDashboard();
  document.querySelector(".service-choice-grid")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** 切换所选日期下的可预约护工页，并清除上一页尚未确认的选择。 */
async function changeAvailableEmployeesPage(page) {
  if (!Number.isInteger(page) || page < 1) return;
  state.availableEmployeesPage = page;
  state.selectedEmployeeId = "";
  state.data.selectedEmployee = null;
  state.selectedTimePeriods = [];
  state.data.selectedEmployeeAvailablePeriods = [];
  await loadAvailableEmployees(false);
  renderUserDashboard();
  document.querySelector(".staff-choice-list")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** 切换预约记录页码，并把视线带回列表顶部。 */
async function changeUserAppointmentsPage(page) {
  if (!Number.isInteger(page) || page < 1) return;
  state.userAppointmentsPage = page;
  applyUserAppointmentPage(await api(userAppointmentsPageUrl(false)));
  renderUserDashboard();
  document.querySelector(".senior-appointments")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/**
 * 生成一条老人预约卡片。
 * 只有待服务预约允许取消；护工电话存在时才显示，避免出现空联系方式。
 */
function renderUserAppointmentCard(item) {
  return `
    <article class="senior-appointment-card">
      <div class="appointment-card-top">
        <h4>${escapeHtml(item.serviceName)}</h4>
        ${seniorStatusBadge(item.status)}
      </div>
      <p class="appointment-date">${formatFriendlyDate(item.appointmentDate)}</p>
      <p class="appointment-time">${formatTimePeriods(item.timePeriods)}</p>
      <div class="appointment-person">
        <p><span>服务人员</span><strong>${escapeHtml(item.employeeName)}</strong></p>
        ${item.employeePhone ? `<p><span>联系电话</span><strong>${escapeHtml(item.employeePhone)}</strong></p>` : ""}
        <p><span>预约金额</span><strong>¥${formatMoney(item.totalAmount)}</strong></p>
      </div>
      ${renderCancellationReason(item)}
      ${item.status === "PENDING" ? `<div class="appointment-card-actions"><button class="btn danger" type="button" data-action="cancel-user" data-id="${item.id}">取消这个预约</button></div>` : ""}
    </article>
  `;
}

/** 把后端英文状态转换为带文字和符号的适老化状态标签。 */
function seniorStatusBadge(status) {
  if (status === "COMPLETED") return `<span class="senior-status completed">✓ 服务已完成</span>`;
  if (status === "CANCELLED") return `<span class="senior-status cancelled">× 预约已取消</span>`;
  return `<span class="senior-status pending">● 已预约，等待服务</span>`;
}

/** 取消后的预约显示发起方和原因；旧数据没有原因时保持为空。 */
function renderCancellationReason(item, compact = false) {
  if (item.status !== "CANCELLED" || !item.cancellationReason) return "";
  const actor = item.cancelledBy === "EMPLOYEE" ? "护工取消原因" : "取消原因";
  return `
    <div class="cancellation-note ${compact ? "compact" : ""}">
      <span>${actor}</span>
      <strong>${escapeHtml(item.cancellationReason)}</strong>
    </div>`;
}

/**
 * 提交新预约。
 * 前端先检查服务和护工是否已选，再把 ID 转成数字并发送给后端；
 * 成功后清理预约步骤并直接展示最新预约记录。
 */
async function bookEmployee(employeeId) {
  if (!state.selectedServiceId) throw new Error("请先选择服务项目");
  if (!state.selectedDate) throw new Error("请先选择服务日期");
  if (!state.selectedTimePeriods.length) throw new Error("请至少选择一个服务时段");
  if (!employeeId) throw new Error("请选择服务人员");
  const date = state.selectedDate || today();
  await api("/appointments", {
    method: "POST",
    body: {
      employeeId: Number(employeeId),
      serviceId: Number(state.selectedServiceId),
      appointmentDate: date,
      timePeriods: state.selectedTimePeriods
    }
  });
  notify("预约成功", "success");
  state.userView = "appointments";
  state.bookingStep = 1;
  state.appointmentsFilter = "";
  state.userAppointmentsPage = 1;
  state.selectedDate = "";
  state.selectedTimePeriods = [];
  state.selectedServiceId = "";
  state.selectedEmployeeId = "";
  state.data.selectedService = null;
  state.data.selectedEmployee = null;
  state.data.selectedEmployeeAvailablePeriods = [];
  await loadUserDashboard();
  renderUserDashboard();
}

/** 经用户二次确认后取消预约，并重新加载列表以显示后端的最终状态。 */
async function cancelUserAppointment(id) {
  if (!confirm("确定要取消这个预约吗？")) return;
  await api(`/appointments/${id}/cancel`, { method: "PATCH" });
  notify("预约已取消", "success");
  await refreshUserAppointments();
}

/* ==================== 护工端 ====================
 * 护工端围绕“接单状态、下一项待服务任务、全部任务和个人信息”组织数据。
 */

/** 加载护工资料、当前任务页和独立任务概况。 */
async function loadEmployeeDashboard() {
  const me = await api("/employees/me");
  state.data.employee = me;
  state.data.employeeAppointments = [];
  state.data.employeeAppointmentPage = normalizePageData([], EMPLOYEE_TASKS_PAGE_SIZE);
  state.data.employeeAppointmentSummary = {};
  state.data.employeeAvailability = [];
  state.data.employeeEarnings = 0;
  state.data.employeeServices = [];
  state.data.employeeCapabilities = [];

  // 未完成学习或尚未通过答题时只需要个人培训状态，不请求工作台数据。
  if (!me.quizPassed) return;
  if (state.employeeView === "profile") return;

  const [appointmentPage, summary, availability, earnings, services, capabilities] = await Promise.all([
    api(employeeTasksPageUrl()),
    api("/employees/me/appointments/summary"),
    api("/employees/me/availability"),
    api("/employees/me/earnings"),
    api("/services"),
    api("/employees/me/capabilities")
  ]);
  const normalizedPage = normalizePageData(appointmentPage, EMPLOYEE_TASKS_PAGE_SIZE);
  state.data.employeeAppointmentPage = normalizedPage;
  state.data.employeeAppointments = normalizedPage.items;
  state.employeeTasksPage = normalizedPage.page;
  state.data.employeeAppointmentSummary = summary || {};
  state.data.employeeAvailability = availability || [];
  state.data.employeeEarnings = Number(earnings || 0);
  state.data.employeeServices = services || [];
  state.data.employeeCapabilities = capabilities || [];
}

function employeeTasksPageUrl() {
  const params = new URLSearchParams({
    page: String(state.employeeTasksPage),
    size: String(EMPLOYEE_TASKS_PAGE_SIZE)
  });
  if (state.appointmentsFilter) params.set("status", state.appointmentsFilter);
  return `/employees/me/appointments/page?${params.toString()}`;
}

/**
 * 计算并渲染护工工作台。
 * “下一项任务”优先选取日期最早且不早于今天的待服务预约，若没有则回退到首条待处理记录。
 */
function renderEmployeeDashboard() {
  const employee = state.data.employee || {};
  if (!employee.trainingCompleted) {
    renderEmployeeTraining();
    return;
  }
  if (!employee.quizPassed) {
    renderEmployeeQuiz();
    return;
  }

  el.viewActions.innerHTML = "";
  if (state.employeeView === "profile") {
    el.viewEyebrow.textContent = "护工个人中心";
    el.viewTitle.textContent = "个人信息";
    el.content.innerHTML = `<div class="employee-view-content">${renderEmployeeProfile(employee)}</div>`;
    return;
  }

  const appointments = state.data.employeeAppointments || [];
  const summary = state.data.employeeAppointmentSummary || {};
  const todayTasks = Number(summary.todayTasks || 0);
  const pendingTasks = Number(summary.pendingTasks || 0);
  const completedToday = Number(summary.completedToday || 0);
  const earnings = state.data.employeeEarnings || 0;
  const nextTask = summary.nextTask || null;

  el.viewEyebrow.textContent = "护工任务中心";
  el.viewTitle.textContent = `${timeGreeting()}，${employee.name || state.name || "您好"}`;
  el.content.innerHTML = `
    <div class="employee-view-content">
        <section class="employee-overview">
          <div class="employee-metrics" aria-label="任务概况">
            <div><span>今日任务</span><strong>${todayTasks}</strong><small>项</small></div>
            <div><span>待服务</span><strong>${pendingTasks}</strong><small>项</small></div>
            <div><span>今日完成</span><strong>${completedToday}</strong><small>项</small></div>
            <div class="income-metric"><span>已获得薪资总额</span><strong>¥${formatMoney(earnings)}</strong><small>按已完成服务参考价统计</small></div>
          </div>
          <label class="employee-status-switch">
            <input id="employeeWorkingToggle" type="checkbox" ${employee.working ? "checked" : ""}>
            <span class="switch-track" aria-hidden="true"><span></span></span>
            <span class="switch-copy">
              <strong>${employee.working ? "接单中" : "暂停接单"}</strong>
              <small>${employee.working ? "可以接收新的服务预约" : "暂时不接收新的服务预约"}</small>
            </span>
          </label>
        </section>

        <div class="employee-profile-reminder">
          <span>请前往“个人信息”确认或修改头像、联系电话和服务介绍，完整资料有助于老人选择服务人员。</span>
          <button type="button" data-action="employee-view" data-view="profile">前往个人信息</button>
        </div>

        ${renderEmployeeAvailability()}
        ${renderEmployeeCapabilities()}
        ${renderEmployeeTaskWorkspace(nextTask, appointments)}
    </div>
  `;
}

/** 护工工作区的两个入口直接放入页面最左侧账号栏。 */
function renderEmployeeSidebarNavigation() {
  const tabs = [
    ["tasks", "工作任务"],
    ["profile", "个人信息"]
  ];
  return `
    <nav class="employee-sidebar-nav" aria-label="护工工作区">
      ${tabs.map(([view, label]) => `<button type="button" class="${state.employeeView === view ? "active" : ""}" data-action="employee-view" data-view="${view}">${label}</button>`).join("")}
    </nav>`;
}

/** 生成可修改的护工个人资料页。 */
function renderEmployeeProfile(employee) {
  const previewEmployee = {
    ...employee,
    avatarData: state.pendingEmployeeAvatar === null
      ? employee.avatarData
      : state.pendingEmployeeAvatar
  };
  return `
    <section class="employee-profile-page">
      <header class="employee-section-heading profile-heading">
        <div><p>公开资料</p><h3>确认并完善个人信息</h3></div>
        <span>姓名、头像和服务介绍会展示给预约老人</span>
      </header>
      <form class="employee-profile-form ${state.employeeProfileDirty ? "has-unsaved-changes" : ""}" data-submit="employee-profile">
        <aside class="employee-avatar-editor">
          <div id="employeeAvatarPreview" class="employee-avatar-preview">
            ${renderEmployeeAvatarContent(previewEmployee)}
          </div>
          <label class="btn secondary avatar-upload-button">
            选择头像
            <input id="employeeAvatarInput" type="file" accept="image/jpeg,image/png,image/webp" hidden>
          </label>
          <button class="btn ghost" type="button" data-action="remove-employee-avatar">移除头像</button>
          <small>支持 JPG、PNG、WebP；图片会自动压缩后上传。</small>
        </aside>
        <div class="employee-profile-fields">
          <label class="field"><span>登录账号</span><input value="${attr(employee.username)}" disabled></label>
          <div class="profile-field-grid">
            <label class="field"><span>真实姓名</span><input name="name" maxlength="50" value="${attr(employee.name)}" required></label>
            <label class="field"><span>年龄</span><input name="age" type="number" min="18" max="100" value="${attr(employee.age)}" required></label>
          </div>
          <label class="field"><span>联系电话</span><input name="phone" inputmode="numeric" pattern="[0-9]{6,20}" maxlength="20" value="${attr(employee.phone)}" required></label>
          <label class="field"><span>从业经历</span><textarea name="experience" maxlength="200" rows="3" placeholder="例如：从事居家照护5年，熟悉行动不便老人照护">${escapeHtml(employee.experience)}</textarea></label>
          <label class="field"><span>个人简介</span><textarea name="bio" maxlength="500" rows="4" placeholder="向老人和家属简单介绍您的服务特点">${escapeHtml(employee.bio)}</textarea></label>
          <div class="profile-save-bar"><span data-profile-save-hint>${state.employeeProfileDirty ? "有尚未保存的修改" : "修改后请及时保存"}</span><button class="btn" type="submit">保存个人信息</button></div>
        </div>
      </form>
    </section>`;
}

/**
 * 新注册护工首先看到岗前学习页。内容聚焦课设需要的基础照护常识，
 * 阅读确认后会把 trainingCompleted 状态持久化，再进入答题环节。
 */
function renderEmployeeTraining() {
  const employee = state.data.employee || {};
  el.viewEyebrow.textContent = "护工岗前培训";
  el.viewTitle.textContent = `欢迎，${employee.name || state.name || "新护工"}`;
  el.viewActions.innerHTML = "";
  el.content.innerHTML = `
    <main class="training-shell">
      ${renderTrainingProgress(1)}
      <header class="training-intro">
        <p>开始服务前，请先完成基础学习</p>
        <h3>护工服务须知</h3>
        <span>请阅读以下四部分内容。完成学习后还需要通过 4 道安全常识题。</span>
      </header>
      <div class="training-module-list">
        <article class="training-module">
          <span class="training-module-number">01</span>
          <div><h4>服务前做好确认</h4><p>到达后先核对老人姓名、服务项目和地址，主动说明身份。开始服务前询问身体状况、行动是否方便及家属交代的注意事项。</p></div>
        </article>
        <article class="training-module">
          <span class="training-module-number">02</span>
          <div><h4>尊重老人并耐心沟通</h4><p>说话放慢速度，使用礼貌称呼，给老人足够的回答时间。不得泄露健康、住址和家庭情况，也不要擅自拍照或发布服务过程。</p></div>
        </article>
        <article class="training-module">
          <span class="training-module-number">03</span>
          <div><h4>留意常见身体问题</h4><p>老人可能出现头晕、跌倒、噎食或突发不适。先保证现场安全并观察意识和呼吸，不要强行搬动，也不要擅自喂药。</p></div>
        </article>
        <article class="training-module">
          <span class="training-module-number">04</span>
          <div><h4>异常情况及时求助</h4><p>遇到无法独立处理的情况，应立即联系家属或平台；出现意识不清、呼吸困难等紧急情况时，应及时拨打 120 并说明准确地址。</p></div>
        </article>
      </div>
      <div class="training-complete-bar">
        <label><input id="trainingAcknowledge" type="checkbox"><span>我已阅读并理解以上服务须知</span></label>
        <button id="completeTrainingButton" class="btn training-primary" type="button" data-action="complete-training" disabled>完成学习，开始答题</button>
      </div>
    </main>
  `;
}

/** 生成岗前学习和安全答题两个培训阶段的进度提示。 */
function renderTrainingProgress(activeStep) {
  const steps = ["岗前学习", "安全答题"];
  return `
    <ol class="training-progress" aria-label="护工入职进度">
      ${steps.map((label, index) => {
        const step = index + 1;
        const status = step < activeStep ? "done" : step === activeStep ? "active" : "";
        return `<li class="${status}"><span>${step < activeStep ? "✓" : step}</span><strong>${label}</strong></li>`;
      }).join("")}
    </ol>
  `;
}

/** 管理员开放权限或护工完成学习后显示四道岗前安全题。 */
function renderEmployeeQuiz() {
  const result = state.employeeQuizResult;
  el.viewEyebrow.textContent = "护工岗前培训";
  el.viewTitle.textContent = "安全常识答题";
  el.viewActions.innerHTML = "";
  el.content.innerHTML = `
    <main class="training-shell quiz-shell">
      ${renderTrainingProgress(2)}
      <header class="training-intro">
        <p>共 4 题，答对至少 3 题即可通过</p>
        <h3>请根据实际服务情况作答</h3>
        <span>未通过可以重新作答。请完成全部题目后再提交。</span>
      </header>
      ${result && !result.passed ? `<div class="quiz-result error"><strong>本次答对 ${result.score} 题</strong><span>还需要答对至少 ${Math.max(0, 3 - result.score)} 题，请检查后重新提交。</span></div>` : ""}
      <form class="training-quiz" data-submit="employee-quiz">
        ${trainingQuestion("q1", "老人不慎跌倒并表示疼痛时，首先应该怎么做？", [
          ["A", "立即把老人拉起来"], ["B", "保证现场安全，观察情况并及时求助"], ["C", "先离开现场寻找其他人"]
        ])}
        ${trainingQuestion("q2", "老人进食时突然无法说话并出现呼吸困难，应该怎么处理？", [
          ["A", "马上给老人喝水"], ["B", "先等待几分钟看看"], ["C", "停止进食，判断噎食情况并立即采取急救或呼救"]
        ])}
        ${trainingQuestion("q3", "老人要求临时改变药量时，正确做法是什么？", [
          ["A", "核对医嘱并联系家属或专业人员确认"], ["B", "根据自己的经验增加药量"], ["C", "使用其他老人的同类药物"]
        ])}
        ${trainingQuestion("q4", "老人情绪焦虑、反复询问同一问题时，应该怎么沟通？", [
          ["A", "严厉制止老人继续询问"], ["B", "保持耐心，放慢语速并重复说明"], ["C", "不回应，继续完成手上的工作"]
        ])}
        <div class="quiz-submit-bar"><span>提交后系统会立即显示结果</span><button class="btn training-primary" type="submit">提交答案</button></div>
      </form>
    </main>
  `;
}

/** 生成一道单选题，选项使用同一 name，保证每题只能选择一个答案。 */
function trainingQuestion(name, title, options) {
  return `
    <fieldset class="training-question">
      <legend><span>${name.replace("q", "")}</span>${escapeHtml(title)}</legend>
      <div class="training-options">
        ${options.map(([value, label]) => `<label><input type="radio" name="${name}" value="${value}" required><span><b>${value}</b>${escapeHtml(label)}</span></label>`).join("")}
      </div>
    </fieldset>
  `;
}

/** 将护工确认完成学习的状态写入后端，然后加载答题页。 */
async function completeEmployeeTraining() {
  await api("/employees/me/training/complete", { method: "PATCH" });
  state.employeeQuizResult = null;
  notify("学习已完成，请参加安全答题", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

/** 提交答题并根据后端评分进入工作台或留在当前页重新作答。 */
async function submitEmployeeQuiz(answers) {
  const result = await api("/employees/me/training/quiz", {
    method: "POST",
    body: { answers }
  });
  state.employeeQuizResult = result;
  if (result.passed) {
    notify(`答对 ${result.score} 题，培训已通过`, "success");
  } else {
    notify(`本次答对 ${result.score} 题，请重新作答`, "error");
  }
  await loadEmployeeDashboard();
  // 首次通过答题时主区域会立即进入工作页，同时重绘侧栏以显示工作任务和个人信息入口。
  renderSession();
  renderEmployeeDashboard();
}

/**
 * 工作台中的星期一至星期日可工作时段表。
 * 已保存的时段来自后端，勾选状态不会只停留在当前浏览器中。
 */
function renderEmployeeAvailability() {
  const selected = new Set(state.data.employeeAvailability || []);
  const days = [[1, "星期一"], [2, "星期二"], [3, "星期三"], [4, "星期四"], [5, "星期五"], [6, "星期六"], [7, "星期日"]];
  const periods = TIME_PERIODS;
  return `
    <section class="employee-availability">
      <div class="employee-section-heading">
        <div><p>工作台功能</p><h3>每周可工作时间</h3></div>
        <span class="availability-note">可多选，修改后请保存</span>
      </div>
      <form data-submit="employee-schedule">
        <div class="availability-bulk-actions">
          <span>快速选择</span>
          <button class="btn secondary" type="button" data-action="select-all-availability">✓ 全部选中</button>
          <button class="btn ghost" type="button" data-action="clear-all-availability">全部清空</button>
        </div>
        <div class="availability-grid" role="group" aria-label="每周可工作时段">
          <div class="availability-corner">时段</div>
          ${days.map(([, label]) => `<div class="availability-heading"><strong>${label}</strong></div>`).join("")}
          ${periods.map(([period, periodLabel, time]) => `
            <div class="availability-period"><strong>${periodLabel}</strong><small>${time}</small></div>
            ${days.map(([day, dayLabel]) => {
              const value = `${day}_${period}`;
              return `<label class="availability-slot" title="${dayLabel}${periodLabel}"><input type="checkbox" name="slot" value="${value}" ${selected.has(value) ? "checked" : ""}><span><b>✓</b>${periodLabel}</span></label>`;
            }).join("")}
          `).join("")}
        </div>
        <div class="availability-save"><span>已选择 <strong data-availability-count>${selected.size}</strong> 个时段</span><button class="btn" type="submit">保存工作时间</button></div>
      </form>
    </section>
  `;
}

/**
 * 在工作时间表下方展示管理员当前上架的服务项目。
 * 服务能力与个人资料分别保存，避免修改时间或能力时误提交姓名、头像等资料。
 */
function renderEmployeeCapabilities() {
  const services = state.data.employeeServices || [];
  const capabilityIds = new Set((state.data.employeeCapabilities || []).map(String));
  return `
    <section class="employee-capabilities">
      <div class="employee-section-heading">
        <div><p>工作台功能</p><h3>可胜任服务</h3></div>
        <span>请选择您能够独立完成的服务项目</span>
      </div>
      <form data-submit="employee-capabilities">
        <fieldset class="employee-capability-fieldset">
          <legend class="sr-only">可胜任服务项目</legend>
          <p>服务项目由管理员统一维护。保存后，老人选择对应服务时才会看到您。</p>
          ${services.length ? `
            <div class="employee-capability-grid">
              ${services.map((service) => `
                <label>
                  <input type="checkbox" name="serviceId" value="${Number(service.id)}" ${capabilityIds.has(String(service.id)) ? "checked" : ""}>
                  <span><b aria-hidden="true">✓</b><strong>${escapeHtml(service.name)}</strong><small>${escapeHtml(service.description || "平台服务项目")}</small></span>
                </label>`).join("")}
            </div>` : `<div class="employee-capability-empty">管理员暂未上架可选择的服务项目</div>`}
        </fieldset>
        <div class="employee-capability-save">
          <span>已选择 <strong data-capability-count>${capabilityIds.size}</strong> 项服务</span>
          <button class="btn" type="submit" ${services.length ? "" : "disabled"}>保存可胜任服务</button>
        </div>
      </form>
    </section>`;
}

/** 保存护工可胜任的服务项目，修改前后都保留明确的二次确认。 */
async function saveEmployeeCapabilities(form) {
  const services = state.data.employeeServices || [];
  const serviceIds = Array.from(form.querySelectorAll('input[name="serviceId"]:checked'))
    .map((input) => Number(input.value))
    .filter(Number.isInteger);
  if (services.length && !serviceIds.length) {
    throw new Error("请至少选择一项您能够胜任的服务");
  }
  if (!confirm(`确定保存这 ${serviceIds.length} 项可胜任服务吗？`)) return;
  await api("/employees/me/capabilities", { method: "PUT", body: { serviceIds } });
  state.data.employeeCapabilities = serviceIds;
  notify("可胜任服务已保存", "success");
  renderEmployeeDashboard();
}

/** 更新服务能力选择区域下方的已选数量。 */
function updateEmployeeCapabilityCounter(form) {
  const counter = form?.querySelector("[data-capability-count]");
  if (counter) counter.textContent = form.querySelectorAll('input[name="serviceId"]:checked').length;
}

/** 收集时间表中的所有复选框并整体保存，空列表表示暂时不安排工作。 */
async function saveEmployeeAvailability(form) {
  const slots = Array.from(form.querySelectorAll('input[name="slot"]:checked')).map((input) => input.value);
  const message = slots.length
    ? `确定保存这 ${slots.length} 个可工作时段吗？保存后老人可在这些时间预约您。`
    : "当前没有选择任何时段，保存后您将不会出现在可预约人员列表中。确定继续吗？";
  if (!confirm(message)) return;
  await api("/employees/me/availability", { method: "PUT", body: { slots } });
  state.data.employeeAvailability = slots;
  notify("可工作时间已保存", "success");
  renderEmployeeDashboard();
}

/** 一键勾选或清空时间表，并立即更新已选数量。 */
function setAllEmployeeAvailability(button, checked) {
  const form = button.closest('form[data-submit="employee-schedule"]');
  if (!form) return;
  form.querySelectorAll('input[name="slot"]').forEach((input) => {
    input.checked = checked;
  });
  updateEmployeeAvailabilityCounter(form);
}

/** 同步工作时间表下方的已选时段数量。 */
function updateEmployeeAvailabilityCounter(form) {
  const counter = form?.querySelector("[data-availability-count]");
  if (counter) counter.textContent = form.querySelectorAll('input[name="slot"]:checked').length;
}

/** 在工作任务和个人信息之间切换；离开有未保存修改的资料页前先征得确认。 */
async function showEmployeeView(view) {
  const nextView = ["tasks", "profile"].includes(view) ? view : "tasks";
  if (nextView === state.employeeView) return;
  if (!confirmDiscardEmployeeProfile()) return;
  state.employeeView = nextView;
  state.appointmentsFilter = "";
  if (nextView === "tasks") state.employeeTasksPage = 1;
  state.pendingEmployeeAvatar = null;
  state.employeeProfileDirty = false;
  await loadEmployeeDashboard();
  renderSession();
  renderEmployeeDashboard();
}

/** 个人资料有修改时询问是否放弃，供切页、刷新和退出入口统一调用。 */
function confirmDiscardEmployeeProfile() {
  if (state.role !== "EMPLOYEE" || state.employeeView !== "profile" || !state.employeeProfileDirty) return true;
  return confirm("个人信息还有未保存的修改。确定放弃修改并离开当前页面吗？");
}

/** 标记资料表单为未保存状态，并更新页面提示。 */
function markEmployeeProfileDirty() {
  if (state.employeeView !== "profile") return;
  state.employeeProfileDirty = true;
  const form = document.querySelector('form[data-submit="employee-profile"]');
  form?.classList.add("has-unsaved-changes");
  const hint = form?.querySelector("[data-profile-save-hint]");
  if (hint) hint.textContent = "有尚未保存的修改";
}

/** 返回头像图片；未上传头像时使用姓名首字作为占位。 */
function renderEmployeeAvatarContent(employee) {
  if (employee?.avatarData) {
    return `<img src="${attr(employee.avatarData)}" alt="${attr(employee.name || "护工")}的头像">`;
  }
  return `<span aria-hidden="true">${escapeHtml(firstCharacter(employee?.name))}</span>`;
}

/** 读取并压缩护工选择的头像，在保存前先更新页面预览。 */
async function previewEmployeeAvatar(file) {
  if (!file) return;
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    throw new Error("请选择 JPG、PNG 或 WebP 图片");
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new Error("原始头像不能超过5MB");
  }

  const avatarData = await compressEmployeeAvatar(file);
  if (avatarData.length > 800000) {
    throw new Error("头像压缩后仍然过大，请选择尺寸更小的图片");
  }
  state.pendingEmployeeAvatar = avatarData;
  updateEmployeeAvatarPreview();
  markEmployeeProfileDirty();
}

/** 把头像最长边缩放到512像素，并使用 WebP 压缩，减少接口和数据库负担。 */
function compressEmployeeAvatar(file) {
  return new Promise((resolve, reject) => {
    const imageUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      try {
        const maxSide = 512;
        const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
        const context = canvas.getContext("2d");
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/webp", 0.82));
      } catch (_) {
        reject(new Error("头像处理失败，请重新选择图片"));
      } finally {
        URL.revokeObjectURL(imageUrl);
      }
    };
    image.onerror = () => {
      URL.revokeObjectURL(imageUrl);
      reject(new Error("无法读取这张图片，请重新选择"));
    };
    image.src = imageUrl;
  });
}

/** 清除当前头像预览，保存个人信息后才会同步到后端。 */
function removeEmployeeAvatarPreview() {
  state.pendingEmployeeAvatar = "";
  const fileInput = document.getElementById("employeeAvatarInput");
  if (fileInput) fileInput.value = "";
  updateEmployeeAvatarPreview();
  markEmployeeProfileDirty();
}

/** 只刷新头像区域，避免护工正在编辑的其他表单字段丢失。 */
function updateEmployeeAvatarPreview() {
  const preview = document.getElementById("employeeAvatarPreview");
  if (!preview) return;
  preview.innerHTML = renderEmployeeAvatarContent({
    ...(state.data.employee || {}),
    avatarData: state.pendingEmployeeAvatar
  });
}

/** 保存护工基本信息、公开介绍以及本次新选择的头像。 */
async function saveEmployeeProfile(payload) {
  const body = {
    name: String(payload.name || "").trim(),
    age: Number(payload.age),
    phone: String(payload.phone || "").trim(),
    experience: String(payload.experience || "").trim(),
    bio: String(payload.bio || "").trim()
  };
  if (state.pendingEmployeeAvatar !== null) {
    body.avatarData = state.pendingEmployeeAvatar;
  }
  await api("/employees/me/profile", { method: "PUT", body });
  state.name = body.name;
  if (localStorage.getItem("eldercare.token")) localStorage.setItem("eldercare.name", body.name);
  if (sessionStorage.getItem("eldercare.token")) sessionStorage.setItem("eldercare.name", body.name);
  state.pendingEmployeeAvatar = null;
  state.employeeProfileDirty = false;
  notify("个人信息已保存", "success");
  await loadEmployeeDashboard();
  renderSession();
  renderEmployeeDashboard();
}

/** 在合并后的工作任务页按后端预约状态重新查询任务。 */
async function filterEmployeeTasks(status) {
  state.employeeView = "tasks";
  state.appointmentsFilter = status;
  state.employeeTasksPage = 1;
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

async function changeEmployeeTasksPage(page) {
  if (!Number.isInteger(page) || page < 1) return;
  state.employeeTasksPage = page;
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
  document.querySelector(".all-task-section")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** 合并“任务首页”和“全部任务”，在同一页面展示下一项任务及可筛选任务记录。 */
function renderEmployeeTaskWorkspace(nextTask, appointments) {
  return `
    <section class="next-task-section">
      <div class="employee-section-heading">
        <div><p>优先处理</p><h3>下一项待服务任务</h3></div>
      </div>
      ${nextTask ? renderEmployeeNextTask(nextTask) : `<div class="employee-empty"><strong>当前没有待服务任务</strong><p>新的预约任务会显示在这里。</p></div>`}
    </section>
    ${renderAllEmployeeTasks(appointments)}
  `;
}

/** 生成最醒目的下一项任务卡，包含日期、服务对象、地址、状态和处理按钮。 */
function renderEmployeeNextTask(task) {
  return `
    <article class="next-task-card">
      <div class="next-task-main">
        <div class="task-date-block"><span>服务日期与时间</span><strong>${formatFriendlyDate(task.appointmentDate)}</strong><small>${formatTimePeriods(task.timePeriods)}</small></div>
        <div><p class="task-service-name">${escapeHtml(task.serviceName)}</p><p class="task-client">服务对象：<strong>${escapeHtml(task.userName)}</strong></p></div>
        ${employeeTaskStatus(task.status)}
      </div>
      <div class="task-address"><span>服务地点</span><strong>${escapeHtml(task.userAddress || "预约中未填写地址")}</strong></div>
      <div class="task-amount"><span>本次服务金额</span><strong>¥${formatMoney(task.totalAmount)}</strong></div>
      ${renderEmployeeTaskActions(task, true)}
    </article>
  `;
}

/** 生成合并工作页中的任务记录，包括状态筛选按钮和任务卡片列表。 */
function renderAllEmployeeTasks(appointments) {
  const filters = [["", "全部"], ["PENDING", "待服务"], ["COMPLETED", "已完成"], ["CANCELLED", "已取消"]];
  const pageData = state.data.employeeAppointmentPage || normalizePageData(appointments, EMPLOYEE_TASKS_PAGE_SIZE);
  return `
    <section class="all-task-section">
      <div class="employee-section-heading"><div><p>任务记录</p><h3>全部工作任务</h3></div><span>共 ${Number(pageData.total || 0)} 条，每页 ${Number(pageData.size || EMPLOYEE_TASKS_PAGE_SIZE)} 条</span></div>
      <div class="employee-task-filter">
        ${filters.map(([value, label]) => `<button type="button" class="${state.appointmentsFilter === value ? "active" : ""}" data-action="employee-task-filter" data-status="${value}">${label}</button>`).join("")}
      </div>
      ${appointments.length ? `<div class="employee-task-list">${appointments.map((task) => renderEmployeeTaskCard(task)).join("")}</div>` : `<div class="employee-empty">当前筛选条件下没有任务。</div>`}
      ${renderPagination(pageData, "employee-tasks-page", "护工任务分页")}
    </section>
  `;
}

/**
 * 生成普通护工任务卡片。
 * @param {Object} task 后端返回的预约任务。
 * @param {boolean} compact 是否使用今日任务中的紧凑布局。
 */
function renderEmployeeTaskCard(task, compact = false) {
  return `
    <article class="employee-task-card ${compact ? "compact" : ""}">
      <div class="employee-task-date"><strong>${formatFriendlyDate(task.appointmentDate)}</strong><span>${formatTimePeriods(task.timePeriods)}</span><small>${escapeHtml(task.serviceName)} · ¥${formatMoney(task.totalAmount)}</small></div>
      <div class="employee-task-client"><span>服务对象</span><strong>${escapeHtml(task.userName)}</strong><small>${escapeHtml(task.userAddress || "未填写地址")}</small></div>
      ${employeeTaskStatus(task.status)}
      ${renderCancellationReason(task)}
      ${renderEmployeeTaskActions(task)}
    </article>
  `;
}

/**
 * 为待服务任务生成可执行操作。
 * 电话联系直接弹出服务对象的号码，不依赖电脑安装拨号软件；查看路线调用高德地图。
 * 已完成和已取消任务不再显示操作。
 */
function renderEmployeeTaskActions(task, prominent = false) {
  if (task.status !== "PENDING") return "";
  return `
    <div class="employee-task-actions ${prominent ? "prominent" : ""}">
      ${task.userPhone
        ? `<button class="btn secondary" type="button" data-action="show-phone" data-phone="${attr(task.userPhone)}" data-name="${attr(task.userName)}">电话联系</button>`
        : `<button class="btn secondary" type="button" disabled>暂无联系电话</button>`}
      ${task.userAddress ? `<button class="btn secondary" type="button" data-action="navigate" data-address="${attr(task.userAddress)}">查看路线</button>` : ""}
      <button class="btn" type="button" data-action="employee-complete" data-id="${task.id}">确认完成服务</button>
      <button class="btn danger" type="button" data-action="employee-cancel" data-id="${task.id}">取消任务</button>
    </div>
  `;
}

/** 在电脑端直接显示联系电话，避免 tel: 协议因没有拨号软件而无法使用。 */
function showContactPhone(phone, name) {
  const number = String(phone || "").trim();
  if (!number) throw new Error("该服务对象暂未提供联系电话");
  const contactName = String(name || "服务对象").trim() || "服务对象";
  window.alert(`${contactName}的联系电话：\n${number}`);
}

/** 把后端预约状态转换成护工端的可读状态标签。 */
function employeeTaskStatus(status) {
  if (status === "COMPLETED") return `<span class="employee-task-status completed">✓ 已完成</span>`;
  if (status === "CANCELLED") return `<span class="employee-task-status cancelled">× 已取消</span>`;
  return `<span class="employee-task-status pending">● 待服务</span>`;
}

/**
 * 更新护工是否接收新预约的状态。
 * 这是“接单中/暂停接单”，与单个任务是否完成是两个独立概念。
 */
async function updateEmployeeStatus(working) {
  const message = working
    ? "确定开始接单吗？开启后，老人可以在您的可工作时间预约服务。"
    : "确定暂停接单吗？暂停后不会接收新预约，但已有任务仍需按时处理。";
  if (!confirm(message)) return false;
  await api("/employees/me/status", { method: "PATCH", body: { isWorking: working } });
  notify(working ? "已进入接单状态" : "已暂停接收新预约", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
  return true;
}

/** 经二次确认后将指定任务标记为完成，并重新加载任务概况。 */
async function employeeComplete(id) {
  if (!confirm("确定这项服务已经完成吗？")) return;
  await api(`/employees/me/appointments/${id}/complete`, { method: "PATCH" });
  notify("预约已标记完成", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

/** 打开护工取消任务表单，预设常见原因并支持填写其他原因。 */
function openEmployeeCancelDialog(id) {
  document.getElementById("employeeCancelDialog")?.remove();
  document.body.insertAdjacentHTML("beforeend", `
    <dialog id="employeeCancelDialog" class="cancel-reason-dialog" aria-labelledby="cancel-dialog-title">
      <form data-submit="employee-cancel-reason" data-appointment-id="${attr(id)}">
        <header>
          <p>取消服务任务</p>
          <h3 id="cancel-dialog-title">请选择取消原因</h3>
          <span>提交后老人和管理员都能看到该原因。</span>
        </header>
        <fieldset class="cancel-reason-options">
          <legend class="sr-only">取消原因</legend>
          ${[
            "临时身体不适",
            "工作时间发生冲突",
            "无法按时到达"
          ].map((reason) => `<label><input type="radio" name="cancelReason" value="${reason}" required><span>${reason}<b aria-hidden="true">✓</b></span></label>`).join("")}
          <label><input type="radio" name="cancelReason" value="OTHER" required><span>其他原因<b aria-hidden="true">✓</b></span></label>
        </fieldset>
        <label class="field cancel-custom-reason" data-custom-cancel-reason hidden>
          <span>请填写其他原因</span>
          <textarea name="customReason" maxlength="200" rows="3" placeholder="请简要说明无法继续服务的原因"></textarea>
          <small>最多输入 200 个字</small>
        </label>
        <footer>
          <button class="btn secondary" type="button" data-action="close-cancel-dialog">暂不取消</button>
          <button class="btn danger" type="submit">确认取消预约</button>
        </footer>
      </form>
    </dialog>`);

  const dialog = document.getElementById("employeeCancelDialog");
  dialog.addEventListener("close", () => dialog.remove(), { once: true });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });
  dialog.showModal();
}

/** 关闭取消原因弹窗，不修改预约状态。 */
function closeEmployeeCancelDialog() {
  const dialog = document.getElementById("employeeCancelDialog");
  if (dialog?.open) dialog.close();
  else dialog?.remove();
}

/** 解析预设或自填原因，提交后重新加载护工任务。 */
async function submitEmployeeCancellation(id, payload) {
  const selectedReason = String(payload.cancelReason || "");
  const reason = selectedReason === "OTHER"
    ? String(payload.customReason || "").trim()
    : selectedReason;
  if (!reason) throw new Error("请选择或填写取消原因");
  if (!confirm(`确定取消这项任务吗？\n取消原因：${reason}`)) return;
  await api(`/employees/me/appointments/${id}/cancel`, {
    method: "PATCH",
    body: { reason }
  });
  closeEmployeeCancelDialog();
  notify("预约已取消", "success");
  await loadEmployeeDashboard();
  renderEmployeeDashboard();
}

/* ==================== 管理员端 ====================
 * 管理员可以维护服务项目、老人用户、护工员工，并查看全平台预约。
 */

/** 并行加载管理员四类数据，减少进入后台时等待多个串行请求的时间。 */
async function loadAdminData() {
  const [servicePage, userPage, employeePage, appointmentPage] = await Promise.all([
    api(adminListPageUrl("services")),
    api(adminListPageUrl("users")),
    api(adminListPageUrl("employees")),
    api(adminAppointmentsPageUrl())
  ]);
  applyAdminListPage("services", servicePage);
  applyAdminListPage("users", userPage);
  applyAdminListPage("employees", employeePage);
  applyAdminAppointmentPage(appointmentPage);
}

function adminListPageUrl(list) {
  const pageStateKey = {
    services: "adminServicesPage",
    users: "adminUsersPage",
    employees: "adminEmployeesPage"
  }[list];
  if (!pageStateKey) throw new Error("管理员分页类型无效");
  const params = new URLSearchParams({
    page: String(state[pageStateKey]),
    size: String(LIST_PAGE_SIZE)
  });
  return `/admin/${list}/page?${params.toString()}`;
}

function applyAdminListPage(list, pageData) {
  const config = {
    services: ["adminServicePage", "adminServices", "adminServicesPage"],
    users: ["adminUserPage", "adminUsers", "adminUsersPage"],
    employees: ["adminEmployeePage", "adminEmployees", "adminEmployeesPage"]
  }[list];
  if (!config) throw new Error("管理员分页类型无效");
  const normalized = normalizePageData(pageData, LIST_PAGE_SIZE);
  state.data[config[0]] = normalized;
  state.data[config[1]] = normalized.items;
  state[config[2]] = normalized.page;
}

async function loadAdminListPage(list) {
  applyAdminListPage(list, await api(adminListPageUrl(list)));
}

/** 只重新读取管理员当前预约页，翻页、筛选和删除时无需重复加载其他后台数据。 */
async function loadAdminAppointmentsPage() {
  const appointmentPage = await api(adminAppointmentsPageUrl());
  applyAdminAppointmentPage(appointmentPage);
}

/** 生成预约分页接口地址，状态为空时不发送 status 参数。 */
function adminAppointmentsPageUrl() {
  const params = new URLSearchParams({
    page: String(state.adminAppointmentsPage),
    size: String(ADMIN_APPOINTMENTS_PAGE_SIZE)
  });
  if (state.appointmentsFilter) params.set("status", state.appointmentsFilter);
  return `/admin/appointments/page?${params.toString()}`;
}

/** 保存分页响应，并使用后端校正后的页码防止删除末页数据后停留在空页。 */
function applyAdminAppointmentPage(pageData) {
  const normalized = pageData && !Array.isArray(pageData)
    ? pageData
    : { items: Array.isArray(pageData) ? pageData : [], page: 1, size: ADMIN_APPOINTMENTS_PAGE_SIZE, total: 0, totalPages: 1, maxTotal: 9999 };
  state.data.adminAppointmentPage = normalized;
  state.data.adminAppointments = normalized.items || [];
  state.adminAppointmentsPage = Number(normalized.page || 1);
}

/** 渲染管理员数据概况、功能标签页和当前标签对应的管理面板。 */
function renderAdminDashboard() {
  const services = state.data.adminServices || [];
  const users = state.data.adminUsers || [];
  const employees = state.data.adminEmployees || [];
  const appointments = state.data.adminAppointments || [];
  el.viewEyebrow.textContent = "当前工作台";
  el.viewTitle.textContent = "管理员后台";
  el.viewActions.innerHTML = `<button class="btn secondary" type="button" data-action="refresh">刷新</button>`;
  el.content.innerHTML = `
    <div class="grid three">
      <section class="card metric"><span class="muted">服务项目</span><strong>${Number(state.data.adminServicePage?.total || services.length)}</strong></section>
      <section class="card metric"><span class="muted">老人用户</span><strong>${Number(state.data.adminUserPage?.total || users.length)}</strong></section>
      <section class="card metric"><span class="muted">护工员工</span><strong>${Number(state.data.adminEmployeePage?.total || employees.length)}</strong></section>
    </div>
    <section class="card">
      <div class="tabs">
        ${adminTab("services", "服务项目")}
        ${adminTab("users", "用户管理")}
        ${adminTab("employees", "员工管理")}
        ${adminTab("appointments", "预约总览")}
      </div>
    </section>
    ${adminPanel()}
  `;
}

/** 生成管理员标签按钮，并根据 activeAdminTab 标记当前选中项。 */
function adminTab(tab, label) {
  return `<button class="tab ${state.activeAdminTab === tab ? "active" : ""}" type="button" data-action="admin-tab" data-tab="${tab}">${label}</button>`;
}

/** 根据管理员当前标签分发到对应面板渲染函数。 */
function adminPanel() {
  if (state.activeAdminTab === "services") return adminServicesPanel();
  if (state.activeAdminTab === "users") return adminUsersPanel();
  if (state.activeAdminTab === "employees") return adminEmployeesPanel();
  return adminAppointmentsPanel();
}

/** 生成新增服务表单和可直接编辑、保存、删除的服务项目表格。 */
function adminServicesPanel() {
  const services = state.data.adminServices || [];
  const pageData = state.data.adminServicePage || normalizePageData(services, LIST_PAGE_SIZE);
  return `
    <section class="card">
      <h3>新增服务项目</h3>
      <form class="split-form" data-submit="create-service">
        <label class="field"><span>名称</span><input name="name" required></label>
        <label class="field"><span>参考价格</span><input name="referencePrice" type="number" min="0" step="0.01" value="0" required></label>
        <label class="field full"><span>说明</span><textarea name="description"></textarea></label>
        <label class="field"><span>预约状态</span><select name="active"><option value="true">可预约</option><option value="false">暂停预约</option></select></label>
        <div class="field row end"><span>&nbsp;</span><button class="btn" type="submit">新增</button></div>
      </form>
    </section>
    <section class="card">
      <div class="admin-list-heading"><h3>服务项目列表</h3><span>共 ${Number(pageData.total || 0)} 条，每页 ${Number(pageData.size || LIST_PAGE_SIZE)} 条</span></div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>名称</th><th>说明</th><th>价格</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            ${services.map((item) => `
              <tr data-service-row="${item.id}">
                <td><input data-field="name" value="${attr(item.name)}"></td>
                <td><input data-field="description" value="${attr(item.description)}"></td>
                <td><input data-field="referencePrice" type="number" min="0" step="0.01" value="${attr(item.referencePrice || 0)}"></td>
                <td>
                  <select data-field="active">
                    <option value="true" ${item.active ? "selected" : ""}>可预约</option>
                    <option value="false" ${!item.active ? "selected" : ""}>暂停预约</option>
                  </select>
                </td>
                <td class="row">
                  <button class="btn secondary" type="button" data-action="save-service" data-id="${item.id}">保存</button>
                  <button class="btn danger" type="button" data-action="delete-service" data-id="${item.id}">删除</button>
                </td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
      ${renderPagination(pageData, "admin-list-page", "服务项目分页", "services")}
    </section>
  `;
}

/** 生成老人用户列表，并提供启用、禁用和删除操作。 */
function adminUsersPanel() {
  const users = state.data.adminUsers || [];
  const pageData = state.data.adminUserPage || normalizePageData(users, LIST_PAGE_SIZE);
  return `
    <section class="card">
      <div class="admin-list-heading"><h3>用户列表</h3><span>共 ${Number(pageData.total || 0)} 条，每页 ${Number(pageData.size || LIST_PAGE_SIZE)} 条</span></div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>姓名</th><th>用户名</th><th>电话</th><th>地址</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>${users.map((user) => `
            <tr>
              <td>${escapeHtml(user.name)}</td>
              <td>${escapeHtml(user.username)}</td>
              <td>${escapeHtml(user.phone)}</td>
              <td>${escapeHtml(user.address)}</td>
              <td>${activeBadge(user.active)}</td>
              <td class="row">
                <button class="btn secondary" type="button" data-action="toggle-user" data-id="${user.id}" data-active="${!user.active}">${user.active ? "禁用" : "启用"}</button>
                <button class="btn danger" type="button" data-action="delete-user" data-id="${user.id}">删除</button>
              </td>
            </tr>
          `).join("")}</tbody>
        </table>
      </div>
      ${renderPagination(pageData, "admin-list-page", "用户列表分页", "users")}
    </section>
  `;
}

/** 生成护工列表，展示接单与账号状态，并提供启用、禁用和删除操作。 */
function adminEmployeesPanel() {
  const employees = state.data.adminEmployees || [];
  const pageData = state.data.adminEmployeePage || normalizePageData(employees, LIST_PAGE_SIZE);
  return `
    <section class="card">
      <div class="admin-list-heading"><h3>员工列表</h3><span>共 ${Number(pageData.total || 0)} 条，每页 ${Number(pageData.size || LIST_PAGE_SIZE)} 条</span></div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>姓名</th><th>用户名</th><th>电话</th><th>培训状态</th><th>接单</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>${employees.map((employee) => `
            <tr>
              <td>${escapeHtml(employee.name)}</td>
              <td>${escapeHtml(employee.username)}</td>
              <td>${escapeHtml(employee.phone)}</td>
              <td>${employeeTrainingBadge(employee)}</td>
              <td>${employee.working ? `<span class="badge success">工作中</span>` : `<span class="badge warning">暂停</span>`}</td>
              <td>${activeBadge(employee.active)}</td>
              <td class="row">
                ${!employee.trainingCompleted ? `<button class="btn secondary" type="button" data-action="grant-employee-training" data-id="${employee.id}">开放答题</button>` : ""}
                <button class="btn secondary" type="button" data-action="toggle-employee" data-id="${employee.id}" data-active="${!employee.active}">${employee.active ? "禁用" : "启用"}</button>
                <button class="btn danger" type="button" data-action="delete-employee" data-id="${employee.id}">删除</button>
              </td>
            </tr>
          `).join("")}</tbody>
        </table>
      </div>
      ${renderPagination(pageData, "admin-list-page", "员工列表分页", "employees")}
    </section>
  `;
}

/** 管理员列表中的培训状态同时使用文字和颜色，避免只依赖颜色判断。 */
function employeeTrainingBadge(employee) {
  if (employee.quizPassed) return `<span class="badge success">✓ 已通过（${employee.quizScore}/4）</span>`;
  if (employee.trainingCompleted) return `<span class="badge warning">待答题</span>`;
  return `<span class="badge danger">未培训</span>`;
}

/** 生成全平台预约总览，并允许管理员按预约状态筛选。 */
function adminAppointmentsPanel() {
  const appointments = state.data.adminAppointments || [];
  const pageData = state.data.adminAppointmentPage || {
    page: 1,
    size: ADMIN_APPOINTMENTS_PAGE_SIZE,
    total: appointments.length,
    totalPages: 1,
    maxTotal: 9999
  };
  return `
    <section class="card">
      <div class="admin-appointments-toolbar">
        <div>
          <h3 class="section-title">全部预约</h3>
          <p>共 <strong>${Number(pageData.total || 0)}</strong> 条记录，系统最多保留 ${Number(pageData.maxTotal || 9999)} 条，每页 ${Number(pageData.size || ADMIN_APPOINTMENTS_PAGE_SIZE)} 条</p>
        </div>
        <select id="adminStatusFilter">
          ${statusOptions()}
        </select>
      </div>
      ${appointmentsTable(appointments, "admin")}
      ${renderAdminAppointmentsPagination(pageData)}
    </section>
  `;
}

/** 管理员预约页码控制，页数只有一页时仍显示总量但隐藏翻页按钮。 */
function renderAdminAppointmentsPagination(pageData) {
  const currentPage = Number(pageData.page || 1);
  const totalPages = Math.max(1, Number(pageData.totalPages || 1));
  if (totalPages <= 1) return "";
  return `
    <nav class="appointments-pagination" aria-label="管理员预约记录分页">
      <button type="button" data-action="admin-appointments-page" data-page="${currentPage - 1}" ${currentPage <= 1 ? "disabled" : ""}>← 上一页</button>
      <span>第 <strong>${currentPage}</strong> 页，共 ${totalPages} 页</span>
      <button type="button" data-action="admin-appointments-page" data-page="${currentPage + 1}" ${currentPage >= totalPages ? "disabled" : ""}>下一页 →</button>
    </nav>`;
}

/** 跳转管理员预约页，并滚回预约管理面板顶部。 */
async function changeAdminAppointmentsPage(page) {
  if (!Number.isInteger(page) || page < 1) return;
  state.adminAppointmentsPage = page;
  await loadAdminAppointmentsPage();
  renderAdminDashboard();
  document.querySelector(".admin-appointments-toolbar")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** 分别切换服务、用户或员工列表页，不影响其他管理员标签的页码。 */
async function changeAdminListPage(list, page) {
  const pageStateKey = {
    services: "adminServicesPage",
    users: "adminUsersPage",
    employees: "adminEmployeesPage"
  }[list];
  if (!pageStateKey || !Number.isInteger(page) || page < 1) return;
  state[pageStateKey] = page;
  await loadAdminListPage(list);
  renderAdminDashboard();
  document.querySelector(".admin-list-heading")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** 永久删除预约前明确提示影响范围，成功后重新加载当前有效页。 */
async function deleteAdminAppointment(id) {
  if (!confirm(`确定永久删除预约 #${id} 吗？\n关联的服务时段、金额和取消原因也会删除，此操作无法恢复。`)) return;
  await api(`/admin/appointments/${id}`, { method: "DELETE" });
  notify("预约记录已删除", "success");
  await loadAdminAppointmentsPage();
  renderAdminDashboard();
}

/**
 * 新增服务项目。
 * 表单中的价格和布尔值先转换为后端要求的类型，成功后刷新管理员全部数据。
 */
async function createService(body) {
  body.referencePrice = Number(body.referencePrice || 0);
  body.active = body.active === "true";
  await api("/admin/services", { method: "POST", body });
  notify("服务项目已新增", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/**
 * 保存表格中被编辑的服务项目。
 * 根据 data-service-row 定位行，再收集所有 data-field 控件形成 PUT 请求体。
 */
async function saveService(id) {
  const row = Array.from(document.querySelectorAll("[data-service-row]"))
    .find((item) => item.dataset.serviceRow === String(id));
  if (!row) throw new Error("未找到要保存的服务项目");
  const body = {};
  row.querySelectorAll("[data-field]").forEach((input) => {
    body[input.dataset.field] = input.value;
  });
  body.referencePrice = Number(body.referencePrice || 0);
  body.active = body.active === "true";
  await api(`/admin/services/${id}`, { method: "PUT", body });
  notify("服务项目已保存", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/** 二次确认后删除服务项目，并刷新后台列表。 */
async function deleteService(id) {
  if (!confirm("确认删除这个服务项目吗？")) return;
  await api(`/admin/services/${id}`, { method: "DELETE" });
  notify("服务项目已删除", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/** 修改老人账号的启用状态；active 是操作后的目标状态。 */
async function toggleUser(id, active) {
  await api(`/admin/users/${id}/active`, { method: "PATCH", body: { active } });
  notify("用户状态已更新", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/** 二次确认后删除老人账号。 */
async function deleteUser(id) {
  if (!confirm("确认删除这个用户吗？")) return;
  await api(`/admin/users/${id}`, { method: "DELETE" });
  notify("用户已删除", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/** 修改护工账号的启用状态；该状态不同于护工自行设置的接单状态。 */
async function toggleEmployee(id, active) {
  await api(`/admin/employees/${id}/active`, { method: "PATCH", body: { active } });
  notify("员工状态已更新", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/** 管理员可以为指定护工跳过阅读确认，直接开放安全答题。 */
async function grantEmployeeTraining(id) {
  if (!confirm("确认要为这名护工开放答题权限吗？")) return;
  await api(`/admin/employees/${id}/training`, { method: "PATCH" });
  notify("已为护工开放答题权限", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/** 二次确认后删除护工账号。 */
async function deleteEmployee(id) {
  if (!confirm("确认删除这个员工吗？")) return;
  await api(`/admin/employees/${id}`, { method: "DELETE" });
  notify("员工已删除", "success");
  await loadAdminData();
  renderAdminDashboard();
}

/* ==================== 管理表格与状态组件 ==================== */

/**
 * 把预约数组渲染成后台表格。
 * mode 用于决定操作列允许出现哪些按钮，目前管理员页面主要使用只读模式。
 */
function appointmentsTable(appointments, mode) {
  if (!appointments.length) return `<div class="empty">暂无预约记录。</div>`;
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>日期与时段</th><th>服务与金额</th><th>老人</th><th>员工</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          ${appointments.map((item) => `
            <tr>
              <td>${escapeHtml(item.appointmentDate)}<br><span class="small muted">${formatTimePeriods(item.timePeriods)}</span></td>
              <td>${escapeHtml(item.serviceName)}<br><span class="small muted">¥${formatMoney(item.totalAmount)}</span></td>
              <td>${escapeHtml(item.userName)}<br><span class="small muted">${escapeHtml(item.userPhone)} ${escapeHtml(item.userAddress)}</span></td>
              <td>${escapeHtml(item.employeeName)}<br><span class="small muted">${escapeHtml(item.employeePhone)}</span></td>
              <td>${statusBadge(item.status)}${renderCancellationReason(item, true)}</td>
              <td>${appointmentActions(item, mode)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

/** 根据预约状态和查看角色生成允许执行的操作，非待服务记录只能查看。 */
function appointmentActions(item, mode) {
  if (mode === "admin") {
    return `<button class="btn danger" type="button" data-action="delete-appointment" data-id="${item.id}" title="永久删除这条预约">删除</button>`;
  }
  if (item.status !== "PENDING") return `<span class="muted small">无可用操作</span>`;
  if (mode === "user") {
    return `<button class="btn danger" type="button" data-action="cancel-user" data-id="${item.id}">取消</button>`;
  }
  if (mode === "employee") {
    const navigationButton = item.userAddress
      ? `<button class="btn secondary" type="button" data-action="navigate" data-address="${attr(item.userAddress)}">高德导航</button>`
      : `<button class="btn secondary" type="button" disabled title="该预约没有可用地址">地址缺失</button>`;
    return `
      <div class="row">
        ${navigationButton}
        <button class="btn secondary" type="button" data-action="employee-complete" data-id="${item.id}">完成</button>
        <button class="btn danger" type="button" data-action="employee-cancel" data-id="${item.id}">取消</button>
      </div>
    `;
  }
  return `<span class="muted small">仅查看</span>`;
}

/** 生成使用通用状态选项的预约筛选下拉框。 */
function filterSelect() {
  return `<select id="appointmentFilter">${statusOptions()}</select>`;
}

/** 生成预约状态 option，并保持 state 中的当前筛选项为选中状态。 */
function statusOptions() {
  const opts = [
    ["", "全部状态"],
    ["PENDING", "待服务"],
    ["COMPLETED", "已完成"],
    ["CANCELLED", "已取消"]
  ];
  return opts.map(([value, label]) => `<option value="${value}" ${state.appointmentsFilter === value ? "selected" : ""}>${label}</option>`).join("");
}

/** 把当前状态筛选转换为 URL 查询参数；未筛选时返回空字符串。 */
function statusQuery() {
  return state.appointmentsFilter ? `?status=${encodeURIComponent(state.appointmentsFilter)}` : "";
}

/** 把预约英文状态转换成后台表格使用的中文徽标。 */
function statusBadge(status) {
  if (status === "COMPLETED") return `<span class="badge success">已完成</span>`;
  if (status === "CANCELLED") return `<span class="badge danger">已取消</span>`;
  return `<span class="badge warning">待服务</span>`;
}

/** 把账号或服务的布尔启用状态转换成中文徽标。 */
function activeBadge(active) {
  return active ? `<span class="badge success">启用</span>` : `<span class="badge danger">禁用</span>`;
}

/** 把分页接口或兼容旧数组统一整理成前端可直接使用的结构。 */
function normalizePageData(pageData, fallbackSize = LIST_PAGE_SIZE) {
  const normalized = pageData && !Array.isArray(pageData)
    ? pageData
    : {
      items: Array.isArray(pageData) ? pageData : [],
      page: 1,
      size: fallbackSize,
      total: Array.isArray(pageData) ? pageData.length : 0,
      totalPages: 1
    };
  return {
    ...normalized,
    items: Array.isArray(normalized.items) ? normalized.items : [],
    page: Math.max(1, Number(normalized.page || 1)),
    size: Math.max(1, Number(normalized.size || fallbackSize)),
    total: Math.max(0, Number(normalized.total || 0)),
    totalPages: Math.max(1, Number(normalized.totalPages || 1))
  };
}

/** 所有业务列表共用的翻页控件，只有超过一页时才显示。 */
function renderPagination(pageData, action, ariaLabel, dataList = "") {
  const normalized = normalizePageData(pageData);
  if (normalized.totalPages <= 1) return "";
  const listAttribute = dataList ? ` data-list="${attr(dataList)}"` : "";
  return `
    <nav class="appointments-pagination" aria-label="${attr(ariaLabel)}">
      <button type="button" data-action="${attr(action)}"${listAttribute} data-page="${normalized.page - 1}" ${normalized.page <= 1 ? "disabled" : ""}>← 上一页</button>
      <span>第 <strong>${normalized.page}</strong> 页，共 ${normalized.totalPages} 页</span>
      <button type="button" data-action="${attr(action)}"${listAttribute} data-page="${normalized.page + 1}" ${normalized.page >= normalized.totalPages ? "disabled" : ""}>下一页 →</button>
    </nav>`;
}

/* ==================== 网络请求与通用工具 ==================== */

/**
 * 所有后端请求的统一入口。
 * 1. 自动拼接 API 根地址并将请求体编码为 JSON；
 * 2. 除公开接口外，自动在 Authorization 请求头中携带 JWT；
 * 3. 统一解析后端 { code, message, data } 结构，只把 data 返回给业务函数；
 * 4. 收到 401 时清除过期会话，其他失败则抛出可供 toast 显示的错误。
 *
 * @param {string} path 以 / 开头的接口相对路径。
 * @param {Object} options 可包含 method、body，以及控制是否认证的 auth。
 * @returns {*} 后端响应对象中的 data 字段。
 */
async function api(path, options = {}) {
  const auth = options.auth !== false;
  const headers = { "Content-Type": "application/json" };
  if (auth && state.token) headers.Authorization = `Bearer ${state.token}`;

  const response = await fetch(`${state.apiBase}${path}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (_) {
    payload = { code: response.status, message: response.statusText || "请求失败", data: null };
  }

  if (response.status === 401 && auth) {
    const message = payload.message || "登录已过期，请重新登录";
    logout(message);
    throw new Error(message);
  }

  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || "请求失败");
  }

  return payload.data;
}

/** 把原生 FormData 转换为普通对象，便于作为 JSON 请求体提交。 */
function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

/**
 * 在表单提交期间统一禁用或恢复所有控件，防止用户连续提交相同请求。
 * 调用方必须在禁用之前读取 FormData。
 */
function setBusy(form, busy) {
  form.querySelectorAll("button, input, select, textarea").forEach((node) => {
    node.disabled = busy;
  });
}

/**
 * 在左下角显示短暂的全局提示。
 * type 可传 success 或 error；新的提示会取消上一个隐藏计时器并重新计时。
 */
function notify(message, type = "") {
  el.toast.textContent = message;
  el.toast.className = `toast ${type}`;
  window.clearTimeout(notify.timer);
  notify.timer = window.setTimeout(() => el.toast.classList.add("hidden"), 2600);
}

/**
 * 使用高德地图 URI API 搜索服务地址。
 * callnative=1 在支持的移动设备上会优先尝试唤起高德地图客户端，否则打开网页地图。
 */
function openAmapNavigation(address) {
  const destination = String(address || "").trim();
  if (!destination) throw new Error("该预约没有可用地址");

  const params = new URLSearchParams({
    keyword: destination,
    view: "map",
    src: "eldercare_service_booking",
    callnative: "1"
  });
  window.open(`https://uri.amap.com/search?${params.toString()}`, "_blank", "noopener,noreferrer");
}

/** 把后端角色代码转换为界面使用的中文名称。 */
function roleName(role) {
  if (role === "USER") return "老人用户";
  if (role === "EMPLOYEE") return "护工员工";
  if (role === "ADMIN") return "管理员";
  return "未登录";
}

/** 把后端金额统一显示为带两位小数和千位分隔符的人民币数字。 */
function formatMoney(value) {
  const amount = Number(value || 0);
  return Number.isFinite(amount)
    ? amount.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : "0.00";
}

/** 把后端时段代码转换为“早间 06:00-10:00”一类可读文本，并保持一天内的时间顺序。 */
function formatTimePeriods(values) {
  const selected = new Set(Array.isArray(values) ? values : []);
  const labels = TIME_PERIODS
    .filter(([value]) => selected.has(value))
    .map(([, label, time]) => `${label} ${time}`);
  return labels.length ? labels.join("、") : "时间待确认";
}

/** 预约金额由服务单个时段参考价乘以老人选择的时段数得到。 */
function calculateBookingTotal(service, slotCount = state.selectedTimePeriods.length) {
  const unitPrice = Number(service?.referencePrice || 0);
  return Number.isFinite(unitPrice) ? unitPrice * Number(slotCount || 0) : 0;
}

/**
 * 按浏览器本地时区返回今天的 YYYY-MM-DD，避免 UTC 转换造成日期提前或推后一天。
 */
function today() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** 返回相对今天偏移指定天数的 YYYY-MM-DD，用于“今天、明天、后天”快捷选择。 */
function offsetDate(offset) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** 把 YYYY-MM-DD 转成“7月13日 星期一”一类更容易阅读的中文日期。 */
function formatFriendlyDate(value) {
  if (!value) return "未选择日期";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return String(value);
  const weekdays = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
  return `${date.getMonth() + 1}月${date.getDate()}日 ${weekdays[date.getDay()]}`;
}

/** 取姓名的第一个 Unicode 字符作为无头像时的文字头像。 */
function firstCharacter(value) {
  return Array.from(String(value || "服务"))[0] || "服";
}

/** 根据浏览器当前小时返回上午好、中午好、下午好或晚上好。 */
function timeGreeting() {
  const hour = new Date().getHours();
  if (hour < 11) return "上午好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
}

/**
 * 转义准备插入 HTML 文本位置的数据，防止姓名、地址等后端内容被解释为标签或脚本。
 */
function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

/** 属性值同样使用 HTML 转义；保留独立函数名便于阅读模板字符串中的数据用途。 */
function attr(value) {
  return escapeHtml(value);
}
