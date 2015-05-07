package org.fogbowcloud.manager.core.plugins.compute.opennebula;

import java.io.IOException;
import java.security.PublicKey;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

public class OpenNebulaSshClientWrapper {

    private SSHClient client;
    private Session session;

    public OpenNebulaSshClientWrapper() {
        
    }

    public OpenNebulaSshClientWrapper(SSHClient ssh) {
        this.client = ssh;
        addBlankHostKeyVerifier(client);
    }

    private void addBlankHostKeyVerifier(SSHClient ssh) {
        ssh.addHostKeyVerifier(new HostKeyVerifier() {
            @Override
            public boolean verify(String arg0, int arg1, PublicKey arg2) {
                return true;
            }
        });
    }

    public SSHClient getSshClient() {
        return client;
    }

    public void connect(String address, int port, String userName,
            String privateKeyFilePath) throws IOException {
        client = new SSHClient();
        addBlankHostKeyVerifier(client);
        client.connect(address, port);
        client.authPublickey(userName, privateKeyFilePath);
    }

    public void disconnect() throws IOException {
        if (session != null) {
            session.close();
            session = null;
        }
        client.disconnect();
        client.close();
    }

    public void doScpUpload(String localFilePath, String remoteFilePath)
            throws IOException {
        FileSystemFile localFile = new FileSystemFile(localFilePath);
        SCPFileTransfer scp = client.newSCPFileTransfer();
        scp.upload(localFile, remoteFilePath);
    }

    public void connect(String host, int port, String userName,
            String privateKeyFilePath, int timeOut) throws IOException {
        client.setConnectTimeout(timeOut);
        connect(host, port, userName, privateKeyFilePath);
    }
}
