package dk.asj.dpm.network.requests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public abstract class Request {
    protected byte command;
    protected byte[] payload;

    public byte[] serialize() {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {

            objectStream.writeObject(this);
            return byteStream.toByteArray();
        } catch (IOException e) {
            System.err.println("Could not serialize request ["+e.getLocalizedMessage()+"]");
            return null;
        }
    }

    public static Request deserialize(byte[] data) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
             ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {

            return (Request) objectStream.readObject();
        } catch (IOException e) {
            System.err.println("Could not read data ["+e.getLocalizedMessage()+"]");
            return null;
        } catch (ClassNotFoundException e) {
            System.err.println("Corrupted data ["+e.getLocalizedMessage()+"]");
            return null;
        }
    }
}
