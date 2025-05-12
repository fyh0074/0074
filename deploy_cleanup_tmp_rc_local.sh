#!/bin/bash

set -e

CLEANUP_SCRIPT="/usr/local/bin/cleanup_tmp_chrome.sh"
RC_LOCAL="/etc/rc.local"

# 権限チェック
if [ "$(id -u)" -ne 0 ]; then
    echo "❌ エラー: このスクリプトはroot権限で実行する必要があります"
    echo "sudoまたはroot権限で実行してください"
    exit 1
fi

echo "🧹 クリーンアップスクリプトを作成: $CLEANUP_SCRIPT"

cat << 'EOF' | tee "$CLEANUP_SCRIPT" > /dev/null
#!/bin/bash
echo "$(date): 一時ディレクトリのクリーンアップを開始..." >> /var/log/cleanup_tmp.log

# 1日以上経過したchrome_user_data_*の一時ディレクトリを削除
find /tmp -maxdepth 1 -type d -name "chrome_user_data_*" -ctime +1 -exec rm -rf {} \; 2>/dev/null || true

# 1日以上経過したdbus-*の一時ディレクトリを削除
find /tmp -maxdepth 1 -type d -name "dbus-*" -ctime +1 -exec rm -rf {} \; 2>/dev/null || true

echo "$(date): クリーンアップ完了" >> /var/log/cleanup_tmp.log
EOF

chmod +x "$CLEANUP_SCRIPT"
echo "✅ 実行権限を設定しました"

# ログディレクトリをチェック
touch /var/log/cleanup_tmp.log
chmod 644 /var/log/cleanup_tmp.log

# /etc/rc.localファイルを設定
echo "⚙️ 起動時のクリーンアップを設定中"

# rc.localファイルが存在するかチェック
if [ -f "$RC_LOCAL" ]; then
    # 既存のファイルをバックアップ
    echo "📁 既存のrc.localファイルをバックアップ"
    cp "$RC_LOCAL" "${RC_LOCAL}.bak"
    
    # すでにスクリプトが含まれているか確認
    if grep -q "$CLEANUP_SCRIPT" "$RC_LOCAL"; then
        echo "🔁 rc.localにはすでにクリーンアップスクリプトが含まれています。スキップします"
    else
        # exit 0の前にスクリプトを追加
        echo "📝 既存のrc.localにクリーンアップスクリプトを追加"
        sed -i "/exit 0/i $CLEANUP_SCRIPT" "$RC_LOCAL"
    fi
else
    # 存在しない場合は新しいファイルを作成
    echo "📄 新しいrc.localファイルを作成"
    tee "$RC_LOCAL" > /dev/null <<EOF
#!/bin/bash
$CLEANUP_SCRIPT
exit 0
EOF
fi

chmod +x "$RC_LOCAL"

# crontabが存在する場合、毎日午前2時に実行するよう設定
if command -v crontab >/dev/null 2>&1; then
    echo "📅 毎日のクリーンアップcronジョブを追加"
    CRON_JOB="0 2 * * * $CLEANUP_SCRIPT"
    
    # cronジョブがすでに存在するか確認
    if crontab -l 2>/dev/null | grep -Fq "$CLEANUP_SCRIPT"; then
        echo "🔁 cronジョブはすでに存在します。スキップします"
    else
        # cronジョブを追加
        (crontab -l 2>/dev/null || echo "") | sed '/cleanup_tmp_chrome/d' | { cat; echo "$CRON_JOB"; } | crontab -
        echo "✅ cronジョブを追加しました"
    fi
    
    # cronサービスを起動
    if command -v service >/dev/null 2>&1; then
        service cron start || true
        echo "🔄 cronサービスを起動しました"
    fi
else
    echo "⚠️ crontabが存在しないため、毎日のタスクをスキップします"
fi

# 即時クリーンアップを実行
echo "🧹 初回クリーンアップを実行中..."
$CLEANUP_SCRIPT

echo "✅ デプロイ完了！"
echo "➡️ 一時ディレクトリのクリーンアップはシステム起動時に自動的に実行されます"
echo "➡️ 毎日午前2時に自動クリーンアップが実行されます"
echo "➡️ クリーンアップログは /var/log/cleanup_tmp.log に保存されます"
