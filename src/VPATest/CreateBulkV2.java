/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VPATest;

import VEOCreate.CreateVEO;
import VEOCreate.Templates;
import VEOGenerator.ArrayDataSource;
import VPA.AppFatal;
import VEOGenerator.Fragment;
import VEOGenerator.VEOGenerator;
import VEOGenerator.PFXUser;
import VEOGenerator.VEOError;
import VERSCommon.VEOFatal;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Andrew
 */
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
public class CreateBulkV2 {

    static String classname = "CreateBulkV3"; // for reporting
    int maxVeos;            // number of VEOs to create
    int maxIOs;             // maximum size of VEO (in IOs)
    FileOutputStream fos;   // underlying file stream for file channel
    Path testConfigDir;     // directory that holds the test configuration
    Path templateDir;       // directory that holds the templates
    Path baseDir;           // directory which to interpret the files in the control file
    Path outputDir;         // directory in which to place the VEOs
    ArrayList<PFXUser> signers; // list of signers
    boolean chatty;         // true if report the start of each VEO
    boolean verbose;        // true if generate lots of detail
    boolean debug;          // true if debugging
    String hashAlg;         // hash algorithm to use
    Templates templates;    // database of templates

    VEOGenerator vg;        // VEO Generator
    Path encDir;            // directory containing encoding templates
    Path srcDir;        // directory containing encodings to be added
    Fragment rData;	// template for record metadata
    Fragment dData;	// template for document metadata
    Fragment fData;     // template for file metadata

    private final static Logger LOG = Logger.getLogger("VPATest.CreateBulkV2");

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
    public CreateBulkV2(String[] args) throws VEOFatal {

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
        signers = new ArrayList<>();
        verbose = false;
        chatty = false;
        debug = false;
        hashAlg = "SHA-512";

        // process command line arguments
        configure(args);

        // read templates
        templateDir = testConfigDir.resolve("templates-99-007");
        encDir = templateDir.resolve("encodingTemplates");
        srcDir = testConfigDir.resolve("content");

        try {
            vg = new VEOGenerator(encDir.toFile(), null);
            rData = Fragment.parseTemplate(templateDir.resolve("record.txt").toFile(), null);
            dData = Fragment.parseTemplate(templateDir.resolve("document.txt").toFile(), null);
            fData = Fragment.parseTemplate(templateDir.resolve("file.txt").toFile(), null);
        } catch (VEOError ve) {
            throw new VEOFatal(ve.getMessage());
        }
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
        Path pfxFile;
        String pfxPasswd;
        String usage = "CreateBulkV3 [-vv] [-v] [-d] -r <#veos> [-m <maxIOs>] [-p <probDepthChange>] -t <templateDir> [-s <pfxFile> <password>] [-o <outputDir>] [-ha <hashAlgorithm] [-copy|move|link] [-e <encoding>]";

        // check for no arguments...
        if (args.length == 0) {
            throw new VEOFatal(classname, 10, "No arguments. Usage: " + usage);
        }

        // process command line arguments
        i = 0;
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

                    // get template directory
                    case "-t":
                        i++;
                        testConfigDir = checkFile("test configuration directory", args[i], true);
                        i++;
                        break;

                    // get pfx file
                    case "-s":
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        pfxPasswd = args[i];
                        try {
                            signers.add(new PFXUser(pfxFile.toString(), pfxPasswd));
                        } catch (VEOError ve) {
                            throw new VEOFatal(classname, 2, "Failed to open PFX file '" + pfxFile.toString() + "': " + ve.getMessage());
                        }
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
                        LOG.log(Level.WARNING, "Unrecognised argument ''{0}''. Usage: {1}", new Object[]{args[i], usage});
                        i++;
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
        LOG.log(Level.INFO, "Test configuration directory is ''{0}''", testConfigDir.toString());
        /*
        if (!signers.isEmpty()) {
            for (i = 0; i < signers.size(); i++) {
                LOG.log(Level.INFO, "Signer is ''{0}''", new Object[]{signers.get(i).toString()});
            }
        } else {
            LOG.log(Level.INFO, "No PFX file specified");
        }
         */
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
     * @throws VEOGenerator.VEOError
     */
    public void buildV2VEOs() throws VEOError {
        String method = "buildVEOs";
        String veoName;
        int seqNo, cnt, i;
        ArrayDataSource ads;
        String[] metadata;
        DirectoryStream<Path> ds;
        boolean nextVEOisRec;
        int fileNo;
        String fileId;

        nextVEOisRec = false;
        fileNo = 1;
        fileId = "";
        for (seqNo = 0; seqNo < maxVeos; seqNo++) {
            if (nextVEOisRec) {
                veoName = "V2VEO-" + seqNo;
                if (chatty) {
                    System.out.println(System.currentTimeMillis() / 1000 + " Starting: " + veoName);
                }
                metadata = null;
                try {

                    // start VEO, adding signature and lock signature blocks
                    vg.startVEO(outputDir.resolve(veoName + ".veo").toFile(), seqNo, 1);
                    for (i = 0; i < signers.size(); i++) {
                        vg.addSignatureBlock(signers.get(i));
                        vg.addLockSignatureBlock(1, signers.get(i));
                    }

                    // start record, including generating the record metadata
                    metadata = new String[5];
                    metadata[0] = "123";
                    metadata[1] = "421";
                    metadata[2] = fileId;
                    metadata[3] = veoName;
                    metadata[4] = "Use of bogies";
                    ads = new ArrayDataSource(metadata);
                    vg.startRecord(rData, ads);

                    // add documents
                    cnt = 0;
                    do {
                        metadata = new String[3];
                        metadata[0] = "VA123";
                        metadata[1] = veoName + "-" + cnt;
                        metadata[2] = "First Doc";
                        ads = new ArrayDataSource(metadata);
                        vg.startDocument(dData, ads);

                        // add encodings
                        try {
                            ds = Files.newDirectoryStream(srcDir);
                            for (Path src : ds) {
                                if (!Files.isRegularFile(src)) {
                                    continue;
                                }
                                vg.addEncoding(src.toFile());
                            }
                            ds.close();
                        } catch (IOException e) {
                            System.err.println("Failed to process directory '" + srcDir.toString() + "': " + e.getMessage());
                        }
                        vg.endDocument();
                        cnt++;
                    } while (Math.random() < 0.4);
                    vg.endRecord();
                    vg.endVEO();
                } catch (VEOError e) {
                    LOG.log(Level.WARNING, "Failed constructing VEO: {0}", new Object[]{e.getMessage()});
                    vg.cleanUpAfterError();
                }
                nextVEOisRec = (Math.random() < 0.9);
            } else {
                veoName = "V2VEO-" + seqNo+"-F";
                fileId = "21/"+fileNo;
                if (chatty) {
                    System.out.println(System.currentTimeMillis() / 1000 + " Starting: " + veoName);
                }
                metadata = null;
                try {

                    // start VEO, adding signature and lock signature blocks
                    vg.startVEO(outputDir.resolve(veoName + ".veo").toFile(), seqNo, 1);
                    for (i = 0; i < signers.size(); i++) {
                        vg.addSignatureBlock(signers.get(i));
                        vg.addLockSignatureBlock(1, signers.get(i));
                    }

                    // start record, including generating the record metadata
                    metadata = new String[4];
                    metadata[0] = "123";
                    metadata[1] = "421";
                    metadata[2] = fileId;
                    metadata[3] = "Use of bogies";
                    ads = new ArrayDataSource(metadata);
                    vg.addFile(fData, ads);
                    vg.endVEO();
                } catch (VEOError e) {
                    LOG.log(Level.WARNING, "Failed constructing VEO: {0}", new Object[]{e.getMessage()});
                    vg.cleanUpAfterError();
                }
                nextVEOisRec = true;
            }
        }
    }

    /**
     * Main program.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateBulkV2 cv;

        if (args.length == 0) {
            // args = new String[]{"-c", "Test/Demo/createANZStests.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
            // args = new String[]{"-c", "Test/Demo/control.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
        }
        try {
            cv = new CreateBulkV2(args);
            cv.buildV2VEOs();
        } catch (VEOFatal | VEOError e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }
}
