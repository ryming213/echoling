# 国内应用商店上架步骤 (App-Store Hardening & Submission Playbook)

> 最后更新：2026-07-04 · 适用版本：`app-release.apk` （88.8 MB，已 R8 + 16KB 对齐 + 用 `echoling-release.keystore` 签名）

本指南覆盖从「已签名的 release APK」到「提交至华为 / 小米 / OPPO / vivo / 应用宝 并通过审核」的全部外部步骤。这些步骤必须由你本人在外部加固平台上完成 —— 它们需要手机号、营业执照、企业认证，**Claude 无权访问这些服务，也无法替你执行**。

---

## 0. 阅读前必备

| 项 | 值 / 路径 |
|---|---|
| 输入 APK | `app/build/outputs/apk/release/app-release.apk`（88.8 MB） |
| 包名（applicationId） | `com.echoling.app` |
| 版本号 | `versionName = 1.0`，`versionCode = 1` |
| 签名证书 DN | `CN=Echo Ling, OU=Mobile, O=Echo Ling, L=Beijing, ST=Beijing, C=CN` |
| 签名证书 SHA-256 | `eb190a2807a9266391b1a59e4b6a4d4fbf671e52c42a2e8e2682023789d3350a` |
| 签名证书 SHA-1 | `36e8529a47e8993e6555d72e43a17680e6f78be2` |
| 签名证书 MD5 | `c6b95411095613b93af54b93e13ca729` |
| 当前已声明权限 | `android.permission.RECORD_AUDIO` |
| 已声明 `<queries>` | `TTS_SERVICE`, `RecognitionService` |

**重要备份**：`echoling/keystore/echoling-release.keystore`（也包括 `keystore.properties`）是更新应用的唯一凭证。丢了就永远不能再以同名包上架。建议：

- 加密 USB 备份（密码管理软件 / BitLocker / VeraCrypt）
- 1Password / 阿里云盘 / 坚果云加密备份
- 打印一份恢复指引放在不同物理位置

---

## 1. 加固（必须 — 360 / 腾讯 / 梆梆 任选其一）

App 提交至国内应用商店前，**必须**对 APK 做加固（动态壳、防二次打包、反调试、防模拟器）。三家在国内是事实上的标准 —— 华为商店本身有「安全加固」合作伙伴列表、小米商店对接腾讯乐固、OPPO / vivo / 应用宝接受 360 加固的 APK。

下面提供三家代表性平台的实际操作步骤。**任选其一即可**。

### 1A. 360 加固保（jiagu.360.cn）

1. 访问 https://jiagu.360.cn/，完成实名认证（手机号 + 营业执照）
2. 控制台 → 「我的应用」→ 「上传应用」
3. 上传 `app-release.apk`
4. 等待约 5-15 分钟处理（视 APK 体积）
5. 处理完成后下载「加固后 APK」+「加固日志」
6. 在 360 控制台查看加固后的「签名指纹」，**该指纹必须与原始 `eb190a28...` 一致**。如果不一致，**不要下载**，说明加固过程破坏了签名，让 360 控制台使用「保留原签名」选项重新加固。

### 1B. 腾讯乐固（console.cloud.tencent.com/ms）

1. 访问 https://console.cloud.tencent.com/ms，注册腾讯云账号并实名
2. 应用管理 → 「上传 APK」
3. 选择「乐固」方案（非 MPaaS 那种企业版）
4. 上传 `app-release.apk`，勾选「保护 VMP 签名」+ 「防调试」+ 「防二次打包」
5. 等待加固完成（约 5-10 分钟）
6. 下载加固后 APK —— 腾讯乐固默认保留原始签名

### 1C. 梆梆加固（www.bangcle.com）

1. 访问 https://www.bangcle.com/，注册企业账号
2. 「产品」→ 「应用加固」→ 上传 `app-release.apk`
3. 选「标准加固」套餐即可
4. 加固完成后下载 APK + 加固报告

### 加固后的验证（必须执行）

无论用哪家加固，**下载后立刻在本地验证**：

```bash
# (Windows 10/11)
"%ANDROID_HOME%\build-tools\34.0.0\apksigner.bat" verify --verbose --print-certs path\to\加固后.apk
```

期望输出（如果签名保留成功）：
- `Signer #1 certificate DN: CN=Echo Ling, OU=Mobile, O=Echo Ling, L=Beijing, ST=Beijing, C=CN`
- `Verified using v2 scheme: true`
- `Verified using v3 scheme: true`

如果 DN 变成 360 的证书或腾讯的证书 → 加固服务签了它们自己，**不能上架**（应用商店会拒绝「签名证书与开发者提交的不符」）。需要：
- 选择加固服务中的「保留原签名」选项
- 或在加固平台上重新签名（用 `keystore/echoling-release.keystore` 重新签名 —— 一般平台都提供这个入口）
- 或联系加固平台客服，要求「二次签名」回原始证书

---

## 2. 重签名（如果加固平台没有保留原签名）

如果第 1 步下载的加固后 APK 签名错误，用 `apksigner` 用我们的密钥库重新签名：

```bash
"%ANDROID_HOME%\build-tools\34.0.0\apksigner.bat" sign \
  --ks echoling/keystore/echoling-release.keystore \
  --ks-key-alias echoling \
  --ks-pass pass:echoling_release_2026 \
  --key-pass pass:echoling_release_2026 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out app-store-release.apk \
  path\to\加固后.apk
```

**之后再次验证**：

```bash
"%ANDROID_HOME%\build-tools\34.0.0\apksigner.bat" verify --verbose --print-certs app-store-release.apk
```

确认 DN 还是 `CN=Echo Ling, ...`。这就是最终提交到应用商店的 APK。

---

## 3. 各应用商店提交清单

### 3A. 华为 AppGallery

- 入口：https://developer.huawei.com/consumer/cn/service/hms/catalog/index.html
- 加固要求：华为商店接受 360 / 腾讯 / 梆梆 任一家加固后的 APK。如果用华为自家的「安全加固」还能再增加一次加固（但意义不大）。
- 隐私声明：必须填《用户协议》、《隐私政策》URL（可放自己的网站 / GitHub Pages）。声明中应说明：
  - 收集的权限：`RECORD_AUDIO`
  - 用途：「跟读练习的本地录音与离线语音识别」
  - 数据流向：「本地处理，不上传服务器」
- 应用内权限使用说明：华为商店审核员会**亲自打开应用查权限说明**——所以这次加的「权限使用说明」页（任务 2 完成）是必填项。
- SHA-256 指纹：在「应用信息」处填写上述 SHA-256，**用于商店开发者身份验证**。

### 3B. 小米应用商店

- 入口：https://dev.mi.com/console/
- 加固要求：小米推荐用 **腾讯乐固** 或接入小米自家的「应用加固」（如选择「安全防护」合作方）。360 加固的 APK 也接受。
- 隐私合规要点：与华为类似；填写 `RECORD_AUDIO` 的具体使用场景。
- 签名一致性：每次更新（版本号升一档）必须用**同一个 keystore** 签名，否则小米提示「包名已存在但签名不一致」，需要走流程解绑或新包名。

### 3C. OPPO 软件商店

- 入口：https://open.oppomobile.com/
- 加固：OPPO 接受加固后的 APK。**OPPO 自家也提供加固服务（OPPO 金融级加固）** —— 也可以走他们自家方案。
- 隐私：填写软件著作权号（你已经过审了），对应到开发者后台「资质」一栏。

### 3D. vivo 应用商店

- 入口：https://dev.vivo.com.cn/
- 与 OPPO 类似，需企业开发者实名，提交营业执照、加固后 APK、《用户协议》、《隐私政策》。

### 3E. 应用宝（腾讯）

- 入口：https://open.tencent.com/app
- 应用宝明显**偏好腾讯乐固加固**（业务关联）。如果选了别家加固，应用宝可能仍接受，但要二次确认。
- 提交时软件著作权证书号是必填项 —— 你通过审核的软著证书扫描件 + 登记号都需要上传。

---

## 4. 商店审核未过的常见情况

| 现象 | 原因 | 解决方案 |
|---|---|---|
| 「签名不一致」 | 加固后没有保留原签名 | 用第 2 步重新签名 |
| 「隐私政策 URL 无法访问」 | URL 填错 / 网站未备案 / 临时挂掉 | 用稳定的 GitHub Pages / 自己的备案域名 |
| 「应用未声明权限使用说明」 | 没看到内置的「权限使用说明」页 | 现在任务 2 已加，确保审核员能看到 —— 在 Me 标签页 |
| 「含有未声明权限」| 加固平台注入了 `READ_PHONE_STATE` 之类 | 换加固平台 / 联系加固客服确认 |
| 「网络权限未声明」 | 加固平台注入了 `INTERNET` | 换加固平台 / 联系加固客服确认 |
| 「应用未提供 root 检测说明」 | 部分商店现在要 | 在隐私政策写「应用检测 root 是为了防止加固失效」 |

---

## 5. 版本升级流程（之后每次发版）

1. **本地改 `versionCode`（+1）和 `versionName`**：改 [app/build.gradle.kts](app/build.gradle.kts:24-25)。`versionCode` 必须严格递增。
2. **`./gradlew :app:assembleRelease`**：得到新的 `app-release.apk`
3. **加固**（按当前选用平台）
4. **重新签名**（如果需要）
5. **验证签名**：`apksigner verify --print-certs` 确认 DN 还是 `CN=Echo Ling`
6. **提交至各商店**，更新说明文本中的新版本号
7. **保留 keystore 备份**（永远）

⚠️ **永远不要换 keystore** —— 「同一个包名 + 不同 keystore」是上架的死结，应用商店会拒绝所有新版本，唯一的修复办法是换包名（即「换应用」）。

---

## 6. 引用

| 主题 | 文档 |
|---|---|
| App 内权限使用说明页面 | [app/src/main/java/com/echoling/app/presentation/ui/screens/permissions/PermissionsScreen.kt](../../app/src/main/java/com/echoling/app/presentation/ui/screens/permissions/PermissionsScreen.kt) |
| Release signing 配置 | [app/build.gradle.kts#signingConfigs](../../app/build.gradle.kts) |
| 16KB 对齐 + 重签名钩子 | [app/build.gradle.kts#16KB-page-size-alignment](../../app/build.gradle.kts) |
| R8 / ProGuard 规则 | [app/proguard-rules.pro](../../app/proguard-rules.pro) |
