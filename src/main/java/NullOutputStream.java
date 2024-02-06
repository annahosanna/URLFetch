import io.OutputStream;

public class NullOutputStream implements OutputStream {

    @Override
    public void write(int b) {
    }

    // This is supposed to have the same behavior
    // as the similar method and therefore also can
    // return an NPE
    @Override
    public void write(byte[] b) {
      if (b == null) {
        throw new NullPointerException();
      }
    }

    // This can return a possible NPE
    @Override
    public void write(byte[] b, int off, int len) {
      if (b == null) {
        throw new NullPointerException();
      }
    }

    @Override
    public String toString() {
      return "NullOutputStream";
    }

}import io.OutputStream;

public class NullOutputStream implements OutputStream {

    @Override
    public void write(int b) {
    }

    // This is supposed to have the same behavior
    // as the similar method and therefore also can
    // return an NPE
    @Override
    public void write(byte[] b) {
      if (b == null) {
        throw new NullPointerException();
      }
    }

    // This can return a possible NPE
    @Override
    public void write(byte[] b, int off, int len) {
      if (b == null) {
        throw new NullPointerException();
      }
    }

    @Override
    public String toString() {
      return "NullOutputStream";
    }

}
