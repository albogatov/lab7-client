package client;

import commons.elements.Worker;

import java.io.Serializable;

public class PreparedCommand implements Serializable {
    protected String cmdLine;
    /**
     * Поле, содержащее описание команды.
     */
    protected String description;
    protected boolean needsObject;
    protected int argumentAmount;
    protected String argument;
    protected String additionalArgument;
    protected Worker object;

    public void setObject(Worker object) {
        this.object = object;
    }

    public void setArgument(String arg) {
        this.argument = arg;
    }

    public void setStringArgs(String arg1, String arg2) {
        this.argument = arg1;
        this.additionalArgument = arg2;
    }

    public void setCmdLine(String line) {
        cmdLine = line;
    }
}
