package dk.asj.dpm.network.requests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Abstract representation of a network packet and functionality for serializing and de-serializing the packet.
 * Concrete representations of a packet should include their relevant data fields.
 */
public abstract class Packet implements Serializable {
    private static final long serialVersionUID = -8543793731549161373L;

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode() ;

    /**
     * Serialize the packet into a byte array.
     * @return the serialized packet.
     * @throws RuntimeException if the serialisation fails.
     */
    public byte[] serialize() throws RuntimeException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {

            objectStream.writeObject(this);
            return byteStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize request ["+e.getLocalizedMessage()+"]");
        }
    }

    /**
     * Construct a packet by de-serializing a byte array.
     * @param data a previously serialized packet.
     * @return the de-serialized packet.
     * @throws RuntimeException if the de-serialization failed.
     */
    public static Packet deserialize(byte[] data) throws RuntimeException{
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
             ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {

            return (Packet) objectStream.readObject();
        } catch (IOException e) {
            throw new RuntimeException("Could not read data ["+e.getLocalizedMessage()+"]");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Corrupted data ["+e.getLocalizedMessage()+"]");
        }
    }
}
