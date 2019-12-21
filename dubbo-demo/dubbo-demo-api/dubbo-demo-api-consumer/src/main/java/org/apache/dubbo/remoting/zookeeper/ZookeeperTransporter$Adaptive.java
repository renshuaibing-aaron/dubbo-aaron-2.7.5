package org.apache.dubbo.remoting.zookeeper;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class ZookeeperTransporter$Adaptive implements ZookeeperTransporter {
    @Override
    public ZookeeperClient connect(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("client", url.getParameter("transporter", "curator"));
        if (extName == null)
            throw new IllegalStateException("Failed to get extension (org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter) name from url (" + url.toString() + ") use keys([client, transporter])");
        ZookeeperTransporter extension = (ZookeeperTransporter) ExtensionLoader.getExtensionLoader(ZookeeperTransporter.class).getExtension(extName);
        return extension.connect(arg0);
    }
}