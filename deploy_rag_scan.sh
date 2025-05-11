#!/bin/bash

# 必要なパッケージがインストールされているか確認
if ! command -v crontab &> /dev/null; then
    echo "Installing cron package..."
    apt-get update && apt-get install -y cron
fi

if ! command -v clamdscan &> /dev/null; then
    echo "Installing clamav package..."
    apt-get update && apt-get install -y clamav clamav-daemon
fi

# ClamAVウイルス定義を更新
echo "Updating ClamAV virus definitions..."
freshclam

# 確認してください、ウイルス定義ディレクトリが存在し、適切な権限が設定されています
mkdir -p /var/lib/clamav
chown -R clamav:clamav /var/lib/clamav
chmod -R 755 /var/lib/clamav

# ClamAVデーモンを起動
echo "Starting ClamAV daemon..."
service clamav-daemon stop || true
service clamav-daemon start || /etc/init.d/clamav-daemon start

# サービスの状態を確認
sleep 5

if ! service clamav-daemon status | grep -q "running"; then
    echo "Error: ClamAV daemon failed to start"
    exit 1
fi

# パラメータを設定
SCRIPT_DIR="/usr/local/bin"
LOG_DIR="/var/log/rag"
WRAPPER_SCRIPT="${SCRIPT_DIR}/scan_wrapper.sh"
USER_SCRIPT="${SCRIPT_DIR}/scan_as_ragadmin.sh"
LOG_FILE="${LOG_DIR}/rag_drives.log"
SCAN_DIR="/home/ragadmin/thinclient_drives/GUACFS"

# ユーザーとグループを作成
if ! id "ragadmin" &>/dev/null; then
    useradd -m -s /bin/bash ragadmin
fi

if ! getent group clamav &>/dev/null; then
    groupadd clamav
fi

# ragadminユーザーをclamavグループに追加
usermod -a -G clamav ragadmin

# ディレクトリを作成
mkdir -p "$SCRIPT_DIR"
mkdir -p "$LOG_DIR"
mkdir -p "$SCAN_DIR"
touch "$LOG_FILE"

# ディレクトリとファイルの権限を設定
chown -R ragadmin:clamav "$SCAN_DIR"
chown ragadmin:clamav "$LOG_FILE"
chmod 775 "$SCAN_DIR"
chmod 664 "$LOG_FILE"
chmod 775 "$LOG_DIR"

# メインスクリプトを書き込み
cat <<'EOF' > "$WRAPPER_SCRIPT"
#!/bin/bash
SCAN_USER="ragadmin"
SCAN_SCRIPT="/usr/local/bin/scan_as_ragadmin.sh"
LOG_FILE="/var/log/rag/rag_drives.log"
DATE_SUFFIX=$(date +%Y-%U)
OLD_LOG="${LOG_FILE}.${DATE_SUFFIX}"

mkdir -p /var/log/rag

if [ -f "$LOG_FILE" ]; then
    if [ ! -f "$OLD_LOG" ]; then
        mv "$LOG_FILE" "$OLD_LOG"
    fi
fi

# 使用 su 代替 sudo
su - "$SCAN_USER" -c "/bin/bash $SCAN_SCRIPT"
EOF

# サブスクリプトを書き込み
cat <<'EOF' > "$USER_SCRIPT"
#!/bin/bash
SCAN_TARGET="/home/ragadmin/thinclient_drives/GUACFS"
LOG_FILE="/var/log/rag/rag_drives.log"
LOCK_FILE="/tmp/scan_rag_drives.lock"

if [ -e "$LOCK_FILE" ]; then
    echo "$(date): Already running, skipping..." >> "$LOG_FILE"
    exit 1
fi

if [ ! -d "$SCAN_TARGET" ]; then
    echo "$(date): Scan target directory does not exist, creating..." >> "$LOG_FILE"
    mkdir -p "$SCAN_TARGET"
    chown ragadmin:clamav "$SCAN_TARGET"
    chmod 775 "$SCAN_TARGET"
fi

if ! find "$SCAN_TARGET" -type f -mmin -2 | grep -q .; then
    echo "$(date): No recent changes, skipping scan." >> "$LOG_FILE"
    exit 0
fi

touch "$LOCK_FILE"
clamscan --log="$LOG_FILE" "$SCAN_TARGET"
rm -f "$LOCK_FILE"
EOF

# 権限を設定
chmod +x "$WRAPPER_SCRIPT"
chmod +x "$USER_SCRIPT"

# cronサービスを起動
service cron start || /etc/init.d/cron start

# rootユーザーのcrontabを設定し、毎分メインスクリプトを実行
CRON_JOB="* * * * * /usr/local/bin/scan_wrapper.sh"
(crontab -l 2>/dev/null | grep -v 'scan_wrapper.sh'; echo "$CRON_JOB") | crontab -

echo "✅ デプロイ完了："
echo "  - スキャンスクリプトは /usr/local/bin/ にインストールされました"
echo "  - ログは $LOG_FILE に出力されます"
echo "  - スキャンディレクトリは $SCAN_DIR です"
echo "  - GUACFSディレクトリの更新ファイルを毎分自動チェックします"
echo "  - サービスの状態を確認するには、以下のコマンドを使用してください："
echo "    - service clamav-daemon status"
echo "    - service cron status"

