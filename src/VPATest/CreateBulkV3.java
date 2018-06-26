/*
 * Copyright Public Record Office Victoria 2018
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2018
 */
package VPATest;

import VEOCreate.CreateVEO;
import VEOCreate.Templates;
import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class creates multiple VEOs for testing purposes. The VEOs vary in
 * number of Information Objects and Information Pieces, but each has the same
 * content files and metadata packages (but with customised titles etc).
 * <h3>Command Line arguments</h3>
 * The following command line arguments must be supplied:
 * <ul>
 * <li><b>-r &lt;count&gt;</b> the number of VEOs to create.</li>
 * <li><b>-t &lt;directory&gt;</b> the directory in which the metadata templates
 * and the standard VEOReadme.txt file will be found. See the last section for
 * details about the metadata templates.</li>
 * </ul>
 * <p>
 * The following command line arguments are optional:
 * <ul>
 * <li><b>-m &lt;count&gt;</b> The maximum number of Information Objects to be
 * created in the VEO.
 * <li><b>-p &lt;probability&gt;</b> The probability, p, that a level change up
 * or down will occur after the construction of this Information Object. The
 * probability of going one deeper is p, the probability of going one shallower
 * is p, and the probability of staying at the same depth is 1-2*p. The VEO will
 * be completed when the depth returns to 1 (or if the maximum number of IOs is
 * generated).
 * <li><b>-v</b> verbose output. By default off.</li>
 * <li><b>-d</b> debug mode. In this mode more logging will be generated, and
 * the VEO directories will not be deleted after the ZIP file is created. By
 * default off.</li>
 * <li><b>-ha &lt;algorithm&gt;</b> The hash algorithm used to protect the
 * content files and create signatures. Valid values are: . The default is
 * 'SHA-1'. The hash algorithm can also be set in the control file.
 * <li><b>-s &lt;PFXfile&gt; &lt;password&gt;</b> a PFX file containing details
 * about the signer (particularly the private key) and the password. The PFX
 * file can also be specified in the control file. If no -s command line
 * argument is present, the PFX file must be specified in the control file.
 * <li><b>-o &lt;outputDir&gt;</b> the directory in which the VEOs are to be
 * created. If not present, the VEOs will be created in the current
 * directory.</li>
 * <li><b>-copy</b> If present, this argument forces content files to be copied
 * to the VEO directory when creating the VEO. This is the slowest option, but
 * it is the most certain to succeed.</li>
 * <li><b>-move</b> If present, the content files will be moved to the VEO
 * directory. This is faster than -copy, but typically can only be performed on
 * the same file system.</li>
 * <li><b>-link</b> If present, the content files will be linked to the VEO
 * directory. This is the fastest option, but may not work on all computer
 * systems and files. -link is the default</li>
 * </ul>
 * <p>
 * A minimal example of usage is<br>
 * <pre>
 *     createVEOs -r 1000 -t templates
 * </pre>
 * <h3>Metadata Templates</h3>
 * The template files are found in the directory specified by the -t command
 * line argument. Templates are used to generate the metadata packages. Each MP
 * or MPC command in the control file specifies a template name (e.g. 'agls').
 * An associated text template file (e.g. 'agls.txt') must exist in the template
 * directory.
 * <p>
 * The template files contains the <i>contents</i> of the metadata package. The
 * contents composed of XML text, which will be included explicitly in each VEO,
 * and substitutions. The start of each substitution is marked by '$$' and the
 * end by '$$'. Possible substitutions are:
 * <ul>
 * <li>
 * $$ date $$ - substitute the current date and time in VERS format</li>
 * <li>
 * $$ [column] &gt;x&gt; $$ - substitute the contents of column &lt;x&gt;. Note
 * that keyword 'column' is optional.</li>
 * <li>
 * $$ file utf8|xml [column] &lt;x&gt; $$ - include the contents of the file
 * specified in column &lt;x&gt;. The file is encoded depending on the second
 * keyword: a 'binary' file is encoded in Base64; a 'utf8' file has the
 * characters &lt;, &gt;, and &amp; encoded; and an 'xml' file is included as
 * is. Note that keyword 'column' is optional.</li>
 * </ul>
 * <p>
 * The MP/MPC commands in the control file contain the information used in the
 * column or file substitutions. Note that the command occupies column 1, and
 * the template name column 2. So real data starts at column 3.
 */
public class CreateBulkV3 {

    static String classname = "CreateBulkV3"; // for reporting
    int maxVeos;            // number of VEOs to create
    int maxIOs;             // maximum size of VEO (in IOs)
    FileOutputStream fos;   // underlying file stream for file channel
    Path testConfigDir;     // directory that holds the test configuration
    Path templateDir;       // directory that holds the templates
    Path controlFile;       // control file to generate the VEOs
    Path baseDir;           // directory which to interpret the files in the control file
    Path outputDir;         // directory in which to place the VEOs
    Path pfxFile;           // signer
    String pfxPasswd;       // signer's password
    boolean chatty;         // true if report the start of each VEO
    boolean verbose;        // true if generate lots of detail
    boolean debug;          // true if debugging
    String hashAlg;         // hash algorithm to use
    Templates templates;    // database of templates
    double probDepthChange; // probability of changing the depth

    private final static Logger LOG = Logger.getLogger("VPATest.CreateBulkV3");

    /**
     * Constructor. Processes the command line arguments to set program up, and
     * parses the metadata templates from the template directory.
     * <p>
     * The defaults are as follows. The templates are found in "./Templates".
     * Output is created in the current directory. The hash algorithm is "SHA1",
     * and the signature algorithm is "SHA1+DSA". Content files are linked to
     * the VEO directory.
     *
     * @param args command line arguments
     * @throws VEOFatal when cannot continue to generate any VEOs
     */
    public CreateBulkV3(String[] args) throws VEOFatal {

        // sanity check
        if (args == null) {
            throw new VEOFatal(classname, 1, "Null command line argument");
        }

        // defaults...
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.setLevel(Level.WARNING);

        maxVeos = 1;
        maxIOs = 1000;
        baseDir = Paths.get(".");
        outputDir = baseDir; // default is the current working directory
        controlFile = null;
        pfxFile = null;
        pfxPasswd = null;
        verbose = false;
        chatty = false;
        debug = false;
        hashAlg = "SHA-512";
        probDepthChange = 0.3333;

        // process command line arguments
        configure(args);

        // read templates
        templateDir = testConfigDir.resolve("templates-15-03");
        templates = new Templates(templateDir);
    }

    /**
     * This method configures the VEO creator from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws VEOFatal if any errors are found in the command line arguments
     */
    private void configure(String args[]) throws VEOFatal {
        int i;
        String usage = "CreateBulkV3 [-vv] [-v] [-d] -r <#veos> [-m <maxIOs>] [-p <probDepthChange>] -t <templateDir> [-s <pfxFile> <password>] [-o <outputDir>] [-ha <hashAlgorithm] [-copy|move|link] [-e <encoding>]";

        // check for no arguments...
        if (args.length == 0) {
            throw new VEOFatal(classname, 10, "No arguments. Usage: " + usage);
        }

        // process command line arguments
        i = 0;
        pfxFile = null;
        pfxPasswd = null;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {

                    // get number of VEOs to create
                    case "-r":
                        i++;
                        try {
                            maxVeos = Integer.parseInt(args[i]);
                        } catch (NumberFormatException nfe) {
                            throw new VEOFatal(classname, 2, "Argument to -r switch must be an integer: '" + args[i] + "': " + nfe.getMessage());
                        }
                        if (maxVeos < 1) {
                            throw new VEOFatal(classname, 2, "Argument to -r switch must greater than 0: '" + maxVeos + "'");
                        }
                        i++;
                        break;

                    // get maximum size of a VEO (in IOs)
                    case "-m":
                        i++;
                        try {
                            maxIOs = Integer.parseInt(args[i]);
                        } catch (NumberFormatException nfe) {
                            throw new VEOFatal(classname, 2, "Argument to -m switch must be an integer: '" + args[i] + "': " + nfe.getMessage());
                        }
                        if (maxIOs < 1) {
                            throw new VEOFatal(classname, 2, "Argument to -m switch must greater than 0: '" + maxIOs + "'");
                        }
                        i++;
                        break;

                    // get probability of a depth change when creating 
                    case "-p":
                        i++;
                        try {
                            probDepthChange = Double.parseDouble(args[i]);
                        } catch (NumberFormatException nfe) {
                            throw new VEOFatal(classname, 2, "Argument to -p switch must be a floating point number: '" + args[i] + "': " + nfe.getMessage());
                        }
                        if (probDepthChange <= 0 || probDepthChange > 0.4) {
                            throw new VEOFatal(classname, 2, "Argument to -p switch must greater than 0 and less than or equal to 0.4: '" + probDepthChange + "'");
                        }
                        i++;
                        break;

                    // get template directory
                    case "-t":
                        i++;
                        testConfigDir = checkFile("test configuration directory", args[i], true);
                        i++;
                        break;

                    // get data file
                    case "-c":
                        i++;
                        controlFile = checkFile("control file", args[i], false);
                        baseDir = controlFile.getParent();
                        i++;
                        break;

                    // get pfx file
                    case "-s":
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        pfxPasswd = args[i];
                        i++;
                        break;

                    // get output directory
                    case "-o":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        i++;
                        break;

                    // get hash algorithm
                    case "-ha":
                        i++;
                        hashAlg = args[i];
                        i++;
                        break;

                    // if verbose...
                    case "-v":
                        chatty = true;
                        i++;
                        LOG.setLevel(Level.INFO);
                        break;

                    // if very verbose...
                    case "-vv":
                        verbose = true;
                        i++;
                        LOG.setLevel(Level.FINE);
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.setLevel(Level.FINEST);

                        break;

                    // if unrecognised arguement, print help string and exit
                    default:
                        throw new VEOFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + usage);
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal(classname, 3, "Missing argument. Usage: " + usage);
        }

        // check to see that user specified a template directory and control file
        if (testConfigDir == null) {
            throw new VEOFatal(classname, 4, "No template directory specified. Usage: " + usage);
        }

        LOG.log(Level.INFO, "Number of VEOs to create: {0}", maxVeos);
        LOG.log(Level.INFO, "Maximum size of VEOs (in IOs): {0}", maxIOs);
        LOG.log(Level.INFO, "Probability of changing depth is ''{0}''", probDepthChange);
        LOG.log(Level.INFO, "Test configuration directory is ''{0}''", testConfigDir.toString());
        if (controlFile != null) {
            LOG.log(Level.INFO, "Control file is ''{0}''", controlFile.toString());
        }
        if (pfxFile != null) {
            LOG.log(Level.INFO, "PFX file is ''{0}'' with password ''{1}''", new Object[]{pfxFile.toString(), pfxPasswd});
        } else {
            LOG.log(Level.INFO, "No PFX file specified");
        }
        LOG.log(Level.INFO, "Output directory is ''{0}''", outputDir.toString());
        LOG.log(Level.INFO, "Hash algorithm is ''{0}''", hashAlg);
        if (LOG.getLevel() == Level.INFO) {
            LOG.log(Level.INFO, "Verbose output is selected");
        } else if (LOG.getLevel() == Level.FINE) {
            LOG.log(Level.FINE, "Very verbose output is selected");
        } else if (LOG.getLevel() == Level.FINEST) {
            LOG.log(Level.FINEST, "Debug mode is selected");
        }
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @throws VEOFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory) throws VEOFatal {
        Path p;

        p = Paths.get(name);

        if (!Files.exists(p)) {
            throw new VEOFatal(classname, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
        }
        if (isDirectory
                && !Files.isDirectory(p)) {
            throw new VEOFatal(classname, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory
                && Files.isDirectory(p)) {
            throw new VEOFatal(classname, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        if (verbose) {
            System.err.println(type + " is '" + p.toAbsolutePath().toString() + "'");
        }
        return p;
    }

    /**
     * Build VEOs specified by the control file. See the start of this file for
     * a description of the control file and the various commands that can
     * appear in it.
     *
     * @throws VEOFatal if an error occurs that prevents any further VEOs from
     * being constructed
     */
    public void buildV3VEOs() throws VEOFatal {
        String method = "buildVEOs";
        String veoName;
        ArrayList<IO> ios;
        int i, j, k, l;
        CreateVEO veo;      // current VEO being created
        String[] metadata;
        String[] eventDesc = {"Created"};
        String[] eventError = {""};
        Path p;
        PFXUser user;
        DirectoryStream<Path> ds;

        veo = null;
        for (i = 0; i < maxVeos; i++) {
            veoName = "V3VEO-" + i;

            // tell the world if verbose...
            if (chatty) {
                System.out.println(System.currentTimeMillis() / 1000 + " Starting: " + veoName);
            }

            // build model of IO
            try {

                // create a directory to contain the content files
                p = outputDir.resolve(veoName);
                if (Files.exists(p)) {
                    try {
                        ds = Files.newDirectoryStream(p);
                        for (Path src : ds) {
                            Files.delete(src);
                        }
                        ds.close();
                    } catch (IOException e) {
                        System.err.println("Failed to process directory '" + p.toString() + "': " + e.getMessage());
                    }
                } else {
                    Files.createDirectory(p);
                }
            } catch (IOException ioe) {
                System.err.println("Failed making output directory: " + ioe.getMessage());
                continue;
            }
            try {
                ios = buildModel(getRealFile("testData/content"), p);
            } catch (VEOError ve) {
                System.err.println("Failed building model: " + ve.getMessage());
                continue;
            }

            metadata = null;
            try {

                // create VEO & add VEOReadme.txt from template directory
                veo = new CreateVEO(outputDir, veoName, hashAlg, debug);
                veo.addVEOReadme(templateDir);

                // add content
                veo.addContent(p);

                // create information objects
                for (j = 0; j < ios.size(); j++) {
                    IO io = ios.get(j);
                    veo.addInformationObject(io.label, io.depth);

                    // add a metadata package to 1st level information object
                    if (j == 0) {
                        metadata = new String[5];
                        metadata[0] = "mp";
                        metadata[1] = "agls";
                        metadata[2] = "http://www.prov.vic.gov.au/record/" + veoName;
                        metadata[3] = veoName;
                        metadata[4] = veoName;
                        veo.addMetadataPackage(templates.findTemplate("agls"), metadata);
                    }

                    // create information pieces in IO
                    for (k = 0; k < io.ips.size(); k++) {
                        IP ip = io.ips.get(k);
                        veo.addInformationPiece(ip.label);

                        // add content files in IO
                        for (l = 0; l < ip.cfs.size(); l++) {
                            Path cf = Paths.get(veoName, ip.cfs.get(l));
                            veo.addContentFile(cf.toString());
                        }
                    }
                }

                // add one event
                veo.addEvent("2018-05-08", "Created", "Andrew Waugh", eventDesc, eventError);

                // finish and sign VEOs
                veo.finishFiles();
                user = new PFXUser(pfxFile.toString(), pfxPasswd);
                veo.sign(user, hashAlg);
                veo.finalise(false);

                // delete the draft content file
                deleteDirectory(p);
                metadata = null;
            } catch (VEOError e) {
                LOG.log(Level.WARNING, "Failed constructing VEO: {0}", new Object[]{e.getMessage()});
                if (veo != null) {
                    veo.abandon(debug);
                }
                veo = null;
                if (e instanceof VEOFatal) {
                    return;
                }
            } finally {
                if (veo != null) {
                    veo.abandon(debug);
                    veo = null;
                }
                if (metadata != null) {
                    metadata = null;
                }
                for (j = 0; j < ios.size(); j++) {
                    ios.get(j).free();
                    ios.clear();
                }
                ios = null;
            }
            if (verbose) {
                System.out.println(System.currentTimeMillis() / 1000 + " Finished");
            }
        }
    }

    /**
     * Build a model of the VEO. The key reason this is necessary is because it
     * is necessary to build the content directory *before* constructing the
     * VEO.
     *
     * @param srcDir directory in which the content files are found
     * @param contentDir directory in which the created content files are put
     * @return a list of Information Objects to construct
     */
    private ArrayList<IO> buildModel(Path srcDir, Path contentDir) {
        int depth;
        ArrayList<IO> ios;
        IO io;
        IP ip;
        int ref, lnkCnt;
        double choice;
        int base;
        String fileName;
        DirectoryStream<Path> ds;
        Path dest;

        ios = new ArrayList<>();
        depth = 1;
        ref = 0;
        lnkCnt = 0;
        base = 0;

        // create list of information objects to create. Create at least one IO.
        // Stop creating when the random walk (depth) returns to 1. Or stop
        // at maxIOs.
        do {

            // create new IO - only top level is labelled
            io = new IO(((ref == 0) ? "root" : ""), depth);

            // create IPs in IO. There is a 0.25 chance of a new IP being
            // started.
            while (Math.random() < 0.25) {
                ip = new IP(null);
                io.ips.add(ip);

                // go through list of content files in the content directory
                // To speed up construction, we link the original content files
                // into this VEOs content directory rather than copy them. We
                // systematically change their name (by adding a prefix) to
                // ensure that each IP has a unique set of files. The only issue
                // is that there is a limited number of links that can be made
                // to each original file. For this reason, each 130 sets of
                // content files, the original file is copied (rather than linked)
                // and then subsequent files are linked.
                try {
                    ds = Files.newDirectoryStream(srcDir);
                    for (Path src : ds) {
                        if (!Files.isRegularFile(src)) {
                            continue;
                        }
                        fileName = src.getFileName().toString();
                        dest = contentDir.resolve(ref + "-" + fileName);
                        if (ref == 0 || lnkCnt == 130) {
                            try {
                                Files.copy(src, dest);
                            } catch (IOException ioe) {
                                System.err.println("Failed to copy '" + dest.toString() + "' from '" + src.toString() + "': " + ioe.getMessage());
                            }
                        } else {
                            Path p1 = contentDir.resolve(base + "-" + fileName);
                            try {
                                Files.createLink(dest, p1);
                            } catch (IOException ioe) {
                                System.err.println("Failed to link '" + dest.toString() + "' to '" + p1.toString() + "': " + ioe.getMessage());
                            }
                        }
                        ip.cfs.add(dest.getFileName().toString());
                    }
                    ds.close();
                } catch (IOException e) {
                    System.err.println("Failed to process directory '" + srcDir.toString() + "': " + e.getMessage());
                }
                if (lnkCnt == 130) {
                    lnkCnt = 0;
                    base = ref;
                } else {
                    lnkCnt++;
                }
                ref++;
            };
            ios.add(io);

            // decide if the next IO is to be at the same level, go down one,
            // or up one.
            choice = Math.random();
            if (choice <= probDepthChange) {
                depth--;
            } else if (choice > (1 - probDepthChange)) {
                depth++;
            }
        } while (depth > 1 && ref < maxIOs);
        return ios;
    }

    /**
     * Generate file reference. The control file contains references to other
     * files. These references may be absolute, they may be relative to the
     * directory containing the control file, or they may be relative to the
     * current working directory. If the file starts with the root (typically a
     * slash), the file ref is absolute. If the file ref starts with a '.', it
     * is considered relative to the current working direction.
     *
     * @param fileRef the file reference from the control file
     * @return the real path of the referenced file or directory
     */
    private Path getRealFile(String fileRef) throws VEOError {
        Path f;
        Properties p;
        String cwd;

        f = Paths.get(fileRef);

        // if it is relative to current working directory
        if (f.startsWith(".")) {
            p = System.getProperties();
            cwd = p.getProperty("user.dir");
            f = Paths.get(cwd, fileRef);

            // if it is absolute (starts at the root)
        } else if (!f.isAbsolute()) {
            f = baseDir.resolve(fileRef);
        }
        try {
            f = f.toRealPath();
        } catch (IOException ioe) {
            throw new VEOError("Invalid file reference in control file: '" + ioe.getMessage() + "'; typically this file doesn't exist");
        }
        return f;
    }

    /**
     * Abandon construction of these VEO and free any resources associated with
     * it.
     *
     * @param debug true if information is to be left for debugging
     */
    public void abandon(boolean debug) {

    }

    /**
     * Recursively delete a directory
     */
    private boolean deleteDirectory(Path directory) {
        DirectoryStream<Path> ds;
        boolean failed;

        failed = false;
        try {
            if (!Files.exists(directory)) {
                return true;
            }
            ds = Files.newDirectoryStream(directory);
            for (Path p : ds) {
                if (!Files.isDirectory(p)) {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        failed = true;
                    }
                } else {
                    failed |= !deleteDirectory(p);
                }
            }
            ds.close();
            if (!failed) {
                Files.delete(directory);
            }
        } catch (IOException e) {
            failed = true;
        }
        return !failed;

    }

    /**
     * Private class representing an Information Object to create in the VEO.
     * This contains the label and depth of the information object, and an array
     * Information Packages
     */
    private class IO {

        String label;       // label of IO
        int depth;          // depth of IO in tree
        ArrayList<IP> ips;  // array of information pieces

        public IO(String label, int depth) {
            this.label = label;
            this.depth = depth;
            ips = new ArrayList<>();
        }

        public void free() {
            int i;

            for (i = 0; i < ips.size(); i++) {
                ips.get(i).free();
            }
            ips.clear();
            ips = null;
        }
    }

    /**
     * Private class representing an Information Piece to create in the VEO.
     * This contains the label and a list of content files to place in this IP.
     */
    private class IP {

        String label;           // label of IP
        ArrayList<String> cfs;  // list of content files

        public IP(String label) {
            this.label = label;
            cfs = new ArrayList<>();
        }

        public void free() {
            cfs.clear();
            cfs = null;
        }
    }

    /**
     * Main program.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateBulkV3 cv;

        if (args.length == 0) {
            // args = new String[]{"-c", "Test/Demo/createANZStests.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
            // args = new String[]{"-c", "Test/Demo/control.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
        }
        try {
            cv = new CreateBulkV3(args);
            cv.buildV3VEOs();
        } catch (VEOFatal e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }
}
