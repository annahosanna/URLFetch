/**
 * URLFetch.java
 *
 * A Java implementation of a command-line URL-fetching utility such
 * as 'wget' or 'curl'.
 *
 * Copyright ® 2010 Christopher Schultz [http://www.christopherschultz.net/].
 *
 * URLFetch by Christopher Schultz is licensed under a
 * Creative Commons Attribution-ShareAlike 3.0 Unported License
 * [http://creativecommons.org/licenses/by-sa/3.0/].
 *
 * You can get the latest copy of this source code from
 * http://www.christopherschultz.net/projects/java/URLFetch.java
 */
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.math.BigInteger;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;

/**
 * A class whose main method fetches a resource from a URL.
 *
 * Usage: java URLFetch [options] [url]
 *
 * Run URLFetch with '--help' or no arguments to get a detailed usage message.
 *
 * @author Christopher Schultz
 * @version 2012-03-26
 */
public class URLFetch
{

    public static void help()
    {
        System.out.println("Usage: java " + URLFetch.class.getName() + " [options] [url]");
        System.out.println();
        System.out.println("   [options]");
        System.out.println("   -no-check-certificate    ");
        System.out.println("   -S    include HTTP headers in output");
        System.out.println("   -O, --output-document write to file instead of stdout");
        System.out.println("   --post-file [file] File containing post data");
        System.out.println("   --post-data [data] String containing post data");
        System.out.println("   --put-file [file] File containing put data");
        System.out.println("   --put-data [data] String containing put data");
        System.out.println("   --mime [mimetype] The mime type to use for request Content-Type");
        System.out.println("   --enforce-content-length Drop all response data after the end of the response's Content-Length");
        System.out.println("   --timing Prints out URL fetch time");
        System.out.println("   --header 'Name: Value' Adds an HTTP header to the request (can be used multiple times)");
        System.exit(0);
    }

    public static void main(String[] args)
        throws IOException
    {
        boolean printHeaders = false;
        boolean doPost = false;
        boolean doPut = false;
        String requestMethod = "GET";
        boolean doTiming = false;
        String targetFilename = null;
        String postFilename = null;
        String postString = null;
        String contentType = null;
        boolean enforceContentLength = false;
        ArrayList<Header> headers = new ArrayList<Header>();

        if(args.length < 1)
            help();

        int argIndex;
        for(argIndex = 0; argIndex < args.length; ++argIndex)
        {
            String arg = args[argIndex];

            if(!arg.startsWith("-"))
                break;

            if("--".equals(arg))
            {
                ++argIndex;
                break;
            }

            if("-no-check-certificate".equals(arg))
                disableSSLCertificateChecking();
            else if("-S".equals(arg))
                printHeaders = true;
            else if("-O".equals(arg) || "--output-document".equals(arg))
                targetFilename = args[++argIndex];
            else if("--post-file".equals(arg))
            {
                postFilename = args[++argIndex];
                doPost = true;
                requestMethod = "POST";
            }
            else if("--post-data".equals(arg))
            {
                postString = args[++argIndex];
                doPost = true;
                requestMethod = "POST";
            }
            else if("--put-file".equals(arg))
            {
                postFilename = args[++argIndex];
                doPut = true;
                requestMethod = "PUT";
            }
            else if("--put-data".equals(arg))
            {
                postString = args[++argIndex];
                doPut = true;
                requestMethod = "PUT";
            }
            else if("--enforce-content-length".equals(arg))
                enforceContentLength = true;
            else if("--mime".equals(arg))
                contentType = args[++argIndex];
            else if("--help".equals(arg))
                help();
            else if("--timing".equals(arg))
                doTiming = true;
            else if("--header".equals(arg))
                headers.add(new Header(args[++argIndex]));
            else if("--".equals(arg))
                break;
        }

        if(argIndex >= args.length)
            help();

        if("true".equalsIgnoreCase(System.getProperty("disable.ssl.cert.checks")))
            disableSSLCertificateChecking();

        URL url = new URL(args[argIndex]);

        boolean reconnect = true; // See redirect handling below
        long elapsed = 0;
        HttpURLConnection conn = null;
        while(reconnect)
        {
            URLConnection c = url.openConnection();

            if(!(c instanceof HttpURLConnection))
                throw new IOException("Expected HttpURLConnection, got " + c);

            /*
              System.err.println("Connection is a " + c);
              sun.net.www.protocol.https.DelegateHttpsURLConnection
              extends sun.net.www.protocol.https.AbstractDelegateHttpsURLConnection
              extends sun.net.www.protocol.http.HttpURLConnection
            */

            conn = (HttpURLConnection)c;

            conn.setDoInput(true);
            conn.setDoOutput(doPost || doPut);

            if(null != headers && 0 < headers.size())
                for(Header header:headers)
                    conn.addRequestProperty(header.getName(),
                                            header.getValue());

            conn.setRequestProperty("User-Agent", "URLFetch/0.9");

            if(doPost || doPut)
            {
                conn.setRequestMethod(requestMethod);

                if(null != contentType)
                    conn.setRequestProperty("Content-Type", contentType);

                if(null != postFilename)
                {
                    File file = new File(postFilename);

                    if(!file.exists())
                        throw new FileNotFoundException(postFilename);

                    conn.setRequestProperty("Content-Length",
                                            String.valueOf(file.length()));
                }
                else if(null != postString)
                {
                    conn.setRequestProperty("Content-Length",
                                            String.valueOf(postString.length()));
                }
            }

            elapsed = System.currentTimeMillis();

            // TODO: Manually follow redirects
            conn.setInstanceFollowRedirects(false);
            conn.connect();

            // POST if necessary
            if(doPost || doPut)
            {
                OutputStream os = conn.getOutputStream();

                if(null != postFilename)
                {
                    BufferedInputStream in = null;

                    try
                    {
                        in = new BufferedInputStream(new FileInputStream(postFilename));

                        byte[] buffer = new byte[4096];
                        int count;
                        while(0 <= (count = in.read(buffer)))
                            os.write(buffer, 0, count);

                        os.flush();
                    }
                    finally
                    {
                        if(null != in) try { in.close(); } catch (IOException ioe)
                        { ioe.printStackTrace(); }
                    }
                }
                else if(null != postString)
                {
                    os.write(postString.getBytes());

                    os.flush();
                }
            }

            switch(conn.getResponseCode())
            {
                case 301:
                    url = new URL(conn.getHeaderField("Location"));
                    break;
                case 302:
                case 307:
                    // Retrieve the URL in the "Location" header using
                    // the same HTTP method
                    url = new URL(conn.getHeaderField("Location"));
                    break;
                case 303:
                    // Retrieve the URL in the "Location" header using GET
                    doPost = false;
                    doPut = false;
                    url = new URL(conn.getHeaderField("Location"));
                    break;
                default:
                    reconnect = false;
                    break;
            }
        }

        if(printHeaders)
        {
            // First, print out the zeroth header (the "response")
            System.out.print("  ");
            System.out.println(conn.getHeaderField(0));

            elapsed = System.currentTimeMillis() - elapsed;

            for(int hi=1; ; ++hi)
            {
                String key = conn.getHeaderFieldKey(hi);

                if(null == key)
                    break;

                System.out.print("  ");
                System.out.print(key);
                System.out.print(": ");
                System.out.println(conn.getHeaderField(hi));
            }

            System.out.println();

            if(doTiming)
                System.out.println("Time-to-headers: " + elapsed + "ms");
        }

        String charset = getCharset(conn);

        if(null == targetFilename)
        {
            // Use the name of the "file" in the URL

            targetFilename = url.getPath();

            if(null != targetFilename)
            {
                int pos = targetFilename.lastIndexOf("/");

                targetFilename = targetFilename.substring(pos + 1);
            }

            if(null == targetFilename || "".equals(targetFilename.trim()))
                targetFilename = "index.html";
        }

        OutputStream out;
        if("-".equals(targetFilename))
        {
            System.out.println("Saving to: `STDOUT`");
            out = System.out;
        }
        else
        {
            System.out.println("Saving to: `" + targetFilename + "`");

            out = new FileOutputStream(targetFilename);
        }

        if(null == charset)
            fetch(conn, new BufferedOutputStream(out), enforceContentLength);
        else
            fetch(conn,
                  new BufferedWriter(new OutputStreamWriter(out, charset)),
                  charset,
                  enforceContentLength);
    }

    public static void fetch(HttpURLConnection conn,
                             OutputStream out,
                             boolean enforceContentLength)
        throws IOException
    {
        BufferedInputStream in = null;

        int responseCode = conn.getResponseCode();

        boolean error = 5 == responseCode / 100
            || 4 == responseCode / 100;

	try
	{
        if(error)
            in = new BufferedInputStream(conn.getErrorStream());
        else
            in = new BufferedInputStream(conn.getInputStream());

        byte[] buffer = new byte[4096];

        String contentLengthString = conn.getHeaderField("Content-Length");

        if(null != contentLengthString && enforceContentLength)
        {
            BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
            BigInteger contentLength = new BigInteger(contentLengthString);

            // Check for huge Content-Length
            if(maxLong.equals(maxLong.max(contentLength)))
            {
                // Oh, good. We can just use a regular long int.
                long want = contentLength.longValue();

                while(0 < want)
                {
                    int n;

                    int wantThisTime = (4096 < want ? 4096 : (int)want);

                    if(-1 != (n = in.read(buffer, 0, wantThisTime)))
                    {
                        out.write(buffer, 0, n);

                        want -= n;
                    }
                }
            }
            else
            {
                // Yuk, we have to deal with huge numbers.
                BigInteger want = contentLength;
                BigInteger bufferSize = BigInteger.valueOf(4096);

                while(BigInteger.ZERO.equals(BigInteger.ZERO.min(want)))
                {
                    int n;

                    int wantThisTime = bufferSize.min(want).intValue();

                    if(-1 != (n = in.read(buffer, 0, wantThisTime)))
                    {
                        out.write(buffer, 0, n);

                        BigInteger read = BigInteger.valueOf(n);

                        want = want.subtract(read);
                    }
                }
            }
        }
        else
        {
            int n;

            // Just write out all the bytes
            while(-1 != (n = in.read(buffer)))
                out.write(buffer, 0, n);
        }

        out.flush();
	}
	finally
	{
          if(null != in) try { in.close(); }
	  catch (IOException ioe)
	  {
            System.err.println("Could not close InputStream");
            ioe.printStackTrace();
	  }
	}
    }

    public static void fetch(HttpURLConnection conn,
                             Writer out,
                             String charset,
                             boolean enforceContentLength)
        throws IOException
    {
        BufferedReader in = null;

        int responseCode = conn.getResponseCode();

        boolean error = 5 == responseCode / 100
            || 4 == responseCode / 100;

	try
	{
        if(error)
            in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), charset));
        else
            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset));

        char[] buffer = new char[4096];

        String contentLengthString = conn.getHeaderField("Content-Length");

        if(null != contentLengthString && enforceContentLength)
        {
            BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
            BigInteger contentLength = new BigInteger(contentLengthString);

            // Check for huge Content-Length
            if(maxLong.equals(maxLong.max(contentLength)))
            {
                // Oh, good. We can just use a regular long int.
                long want = contentLength.longValue();

                while(0 < want)
                {
                    int n;

                    int wantThisTime = (4096 < want ? 4096 : (int)want);

                    if(-1 != (n = in.read(buffer, 0, wantThisTime)))
                    {
                        // Check the number of bytes; truncate if necessary
                        byte[] bytes = new String(buffer, 0, n).getBytes(charset);

                        if(bytes.length > want)
                        {
                            buffer = new String(bytes, 0, (int)want, charset).toCharArray();
                            out.write(buffer);

                            want = 0;
                        }
                        else
                        {
                            out.write(buffer, 0, n);

                            want -= n;
                        }
                    }
                }
            }
            else
            {
                // Yuk, we have to deal with huge numbers.
                BigInteger want = contentLength;
                BigInteger bufferSize = BigInteger.valueOf(4096);

                while(BigInteger.ZERO.equals(BigInteger.ZERO.min(want)))
                {
                    int n;

                    int wantThisTime = bufferSize.min(want).intValue();

                    if(-1 != (n = in.read(buffer, 0, wantThisTime)))
                    {
                        // Check the number of bytes; truncate if necessary
                        byte[] bytes = new String(buffer, 0, n).getBytes(charset);

                        if(want.equals(want.min(BigInteger.valueOf(bytes.length))))
                        {
                            buffer = new String(bytes, 0, want.intValue(), charset).toCharArray();

                            out.write(buffer);

                            want = BigInteger.ZERO;
                        }
                        else
                        {
                            out.write(buffer, 0, n);

                            BigInteger read = BigInteger.valueOf(n);

                            want = want.subtract(read);
                        }
                    }
                }
            }
        }
        else
        {
            int n;

            // Just write out all the bytes
            while(-1 != (n = in.read(buffer)))
                out.write(buffer, 0, n);
        }

        out.flush();
        }
        finally
        {
          if(null != in) try { in.close(); }
          catch (IOException ioe)
          {
            System.err.println("Could not close InputStream");
            ioe.printStackTrace();
          }
        }
    }

    public static String getCharset(URLConnection conn)
    {
        String charset = null;

        // This appears to be for "gzip" and the like, not for
        // character encoding
        //  = conn.getHeaderField("Content-Encoding");

        String contentType = conn.getHeaderField("Content-Type");

        if(null != contentType)
        {
            int pos = contentType.indexOf("; charset=");

            if(pos >= 0)
            {
                charset = contentType.substring(pos + 10);
            }
            else if(contentType.startsWith("text/"))
            {
                // See HTTP 1.1 specification, section 3.7.1
                charset = "ISO-8859-1";
            }
        }

        return charset;
    }

    public static void disableSSLCertificateChecking()
    {
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs,
                                               String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs,
                                               String authType) {
                }
            }
        };

        try
        {
            SSLContext sc = SSLContext.getInstance("SSL");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());
	    
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        }
        catch (KeyManagementException kme)
        {
            kme.printStackTrace();
        }
        catch (NoSuchAlgorithmException nsae)
        {
            nsae.printStackTrace();
        }
    }

    static class Header
    {
        private String _name;
        private String _value;

        public Header(String header)
        {
            int pos = header.indexOf(':');

            if(0 < pos)
            {
                _name = header.substring(0, pos).trim();
                _value = header.substring(pos + 1).trim();
            }
            else
            {
                _name = header;
                _value = "";
            }
        }

        public String getName() { return _name; }
        public String getValue() { return _value; }
    }
}
