import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class Login extends JDialog implements ActionListener{
    private JTextField playerNameField;
    private JTextField hostNameField;
    private JButton okButton;
    private JButton cancelButton;
    private String ip;
    private String name;
    
    public Login(JFrame frame){
        super(frame, true);
        JPanel messagePanel = new JPanel();
        getContentPane().add(messagePanel);
        playerNameField = new JTextField(null, 10);
        hostNameField = new JTextField("localhost", 10);
        okButton = new JButton("Начать");
        cancelButton = new JButton("Выйти");
        okButton.addActionListener(this);
        cancelButton.addActionListener(this);
        messagePanel.setLayout(new GridLayout(0,2));
        messagePanel.add(new JLabel("Игрок: "));
        messagePanel.add(playerNameField);
        messagePanel.add(new JLabel("IP-адрес сервера: "));
        messagePanel.add(hostNameField);
        messagePanel.add(okButton);
        messagePanel.add(cancelButton);
        pack();
        setLocationRelativeTo(frame);
        setVisible(true);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            name = playerNameField.getText();
            ip = hostNameField.getText();
            dispose();
        }
        
        if (e.getSource() == cancelButton) {
            System.exit(1);
        }
    }

    public String getServerIpAddress() {
        return ip;
    }
}
