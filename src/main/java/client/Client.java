package client;

import commons.app.Command;
import commons.app.CommandCenter;
import commons.commands.Help;
import commons.elements.Worker;
import commons.utils.UserInterface;
import commons.utils.SerializationTool;

import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.UnresolvedAddressException;
import java.util.*;

public class Client implements Runnable {
    private SocketAddress socketAddress;
    private final DatagramChannel datagramChannel;
    private final Selector selector;
    private final UserInterface userInterface = new UserInterface(new InputStreamReader(System.in),
            new OutputStreamWriter(System.out), true);

    public Client() throws IOException {
        selector = Selector.open();
        datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        userInterface.displayMessage("Осуществляется подключение в неблокирующем режиме ввода-вывода");
    }

    public static void main(String[] args) throws FileNotFoundException {
        UserInterface userInterface = new UserInterface(new InputStreamReader(System.in),
                new OutputStreamWriter(System.out), true);
        try {
            String host = userInterface.readUnlimitedArgument("Введите адрес подключения:", false);
            Integer port = null;
            while (port == null) {
                try {
                    port = Integer.parseInt(userInterface.readLimitedArgument("Введите порт:", 1025, 65535, false));
                } catch (NumberFormatException e) {
                    userInterface.displayMessage("Порт должен быть числом");
                }
            }
            Client client = new Client();
            client.connect(host, port);
            client.run();
            while (true) {
                String confirmation = userInterface.readUnlimitedArgument("Сервер временно недоступен, хотите повторить подключение? (да/нет)", false);
                if (confirmation.equals("да")) {
                    client.run();
                } else
                    System.exit(0);
            }
        } catch (BindException e) {
            userInterface.displayMessage("Подключение по данному порту невозможно или у вас нет на него прав");
            PrintWriter pw = new PrintWriter("errorLog.txt");
            e.printStackTrace(pw);
            pw.close();
            System.exit(-1);
        } catch (IOException | UnresolvedAddressException e) {
            userInterface.displayMessage("Подключение по данному адресу не удалось");
            PrintWriter pw = new PrintWriter("errorLog.txt");
            e.printStackTrace(pw);
            pw.close();
            System.exit(-1);
        } catch (NoSuchElementException e) {
            userInterface.displayMessage("Ввод недоступен");
            PrintWriter pw = new PrintWriter("errorLog.txt");
            e.printStackTrace(pw);
            pw.close();
            System.exit(-1);
        }
    }

    public void connect(String host, int port) throws IOException {
        socketAddress = new InetSocketAddress(host, port);
        datagramChannel.connect(socketAddress);
        userInterface.displayMessage("Осуществляется подключение по адресу " + host + " по порту " + port);
    }

    public void sendCommand(Command cmd) throws IOException {
        if (SerializationTool.serializeObject(cmd) == null) {
            userInterface.displayMessage("Произошла ошибка сериализации");
            System.exit(-1);
        }
        ByteBuffer send = ByteBuffer.wrap(Objects.requireNonNull(SerializationTool.serializeObject(cmd)));
        datagramChannel.send(send, socketAddress);
    }

    private byte[] receiveAnswer() throws IOException {
        byte[] buffer = new byte[65536];
        ByteBuffer bufferAnswer = ByteBuffer.wrap(buffer);
        socketAddress = datagramChannel.receive(bufferAnswer);
        return bufferAnswer.array();
    }

    @Override
    public void run() {
        try {
            boolean availability = false;
            Scanner scanner = new Scanner(System.in);
            datagramChannel.register(selector, SelectionKey.OP_READ);
            sendCommand(new Help());
            while (true) {
                int count = selector.select();
                if (count == 0) {
                    System.exit(0);
                }
                Set keys = selector.selectedKeys();
                Iterator iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = (SelectionKey) iterator.next();
                    iterator.remove();
                    if (selectionKey.isReadable()) {
                        byte[] toBeReceived = receiveAnswer();
                        String answer = (String) new SerializationTool().deserializeObject(toBeReceived);
                        if (!availability) {
                            userInterface.displayMessage("Подключение успешно! Список возможных команд:" + "\n" + answer);
                            availability = true;
                        }
                        if (!answer.contains("Awaiting further client instructions.")) {
                            userInterface.displayMessage(answer);
                            if (answer.equals("Коллекция сохранена в файл")) {
                                userInterface.displayMessage("До новых встреч");
                                System.exit(0);
                            }
                        } else datagramChannel.register(selector, SelectionKey.OP_WRITE);
                    }
                    if (selectionKey.isWritable()) {
                        datagramChannel.register(selector, SelectionKey.OP_READ);
                        String input = scanner.nextLine().trim();
                        String[] args = input.split("\\s+");
                        if (args[0].equals("save")) {
                            userInterface.displayMessage("Данная команда недоступна пользователю");
                            datagramChannel.register(selector, SelectionKey.OP_WRITE);
                        } else {
                            Command cmd = CommandCenter.getInstance().getCmd(args[0]);
                            if (!(cmd == null)) {
                                if (cmd.getArgumentAmount() == 0) {
                                    sendCommand(cmd);
                                }
                                if (cmd.getArgumentAmount() == 1 && cmd.getNeedsObject()) {
                                    Worker worker = userInterface.readWorker(userInterface);
                                    cmd.setObject(worker);
                                    sendCommand(cmd);
                                }
                                if (cmd.getArgumentAmount() == 1 && !cmd.getNeedsObject()) {
                                    cmd.setArgument(args[1]);
                                    sendCommand(cmd);
                                }
                                if (cmd.getArgumentAmount() == 2 && cmd.getNeedsObject()) {
                                    Worker worker = userInterface.readWorker(userInterface);
                                    cmd.setArgument(args[1]);
                                    cmd.setObject(worker);
                                    sendCommand(cmd);
                                }
                            } else {
                                userInterface.displayMessage("Введена несуществующая команда, используйте команду help, " +
                                        "чтобы получить список возможных команд");
                                datagramChannel.register(selector, SelectionKey.OP_WRITE);
                            }
                        }
                    }
                }
            }
        } catch (PortUnreachableException e) {
            userInterface.displayMessage("Порт недоступен");
        } catch (IOException e) {
            userInterface.displayMessage("Произошла неизвестная ошибка ввода-вывода");
            System.exit(-1);
        }
    }
}