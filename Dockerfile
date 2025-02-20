FROM ubuntu:latest

# 安装必要的软件包和添加Google Chrome的APT源
RUN apt-get update && apt-get install -y \
    wget \
    gnupg2 \
    openbox \
    xrdp \
    ssh \
    dbus \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y google-chrome-stable \
    && useradd -m http_rag \
    && echo "http_rag:Admin00" | chpasswd

# 配置xrdp
RUN sed -i 's/allowed_users=console/allowed_users=anybody/' /etc/X11/Xwrapper.config

# 创建所需的目录
RUN mkdir -p /etc/X11/xinit/xinitrc.d

# 添加Openbox会话到全局配置
RUN echo "exec openbox-session" > /etc/X11/xinit/xinitrc.d/99-openbox-session

# 创建全局autostart文件并添加Chrome启动命令
RUN mkdir -p /etc/xdg/openbox && \
    echo "google-chrome --start-fullscreen --noerrdialogs --disable-session-crashed-bubble --disable-infobars &" > /etc/xdg/openbox/autostart

# 创建动态权限设置脚本
RUN echo '#!/bin/bash\nUSER=$(whoami)\nchmod 777 /home/$USER/thinclient_drives\n' > /usr/local/bin/set_permissions.sh && \
    chmod +x /usr/local/bin/set_permissions.sh

# 修改全局配置以在用户登录时执行脚本
RUN echo "/usr/local/bin/set_permissions.sh" >> /etc/xrdp/sesman.ini

# 启动xrdp服务
CMD /etc/init.d/xrdp start && tail -f /dev/null