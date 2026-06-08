package com.nicch.mediatar;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.xml.sax.InputSource;

public class ProjectSerializer {

    // 128-bit static key for AES encryption
    private static final byte[] KEY_BYTES = "MediatarKeySec26".getBytes(StandardCharsets.UTF_8);

    private static byte[] encrypt(byte[] plaintext) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(KEY_BYTES, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(byte[] ciphertext) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(KEY_BYTES, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(ciphertext);
    }

    public static void saveProject(File file, List<MainWorkhorse.MediaItem> mediaItems, List<MainWorkhorse.TimelineTrack> tracks) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        Element root = doc.createElement("project");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);

        // Media Pool
        Element mediaPoolNode = doc.createElement("mediaPool");
        root.appendChild(mediaPoolNode);
        for (MainWorkhorse.MediaItem item : mediaItems) {
            Element fileNode = doc.createElement("file");
            fileNode.appendChild(doc.createTextNode(item.getFile().getAbsolutePath()));
            mediaPoolNode.appendChild(fileNode);
        }

        // Tracks
        Element tracksNode = doc.createElement("tracks");
        root.appendChild(tracksNode);
        for (MainWorkhorse.TimelineTrack track : tracks) {
            Element trackNode = doc.createElement("track");
            trackNode.setAttribute("name", track.getName());
            trackNode.setAttribute("type", track.getType().name());
            tracksNode.appendChild(trackNode);

            for (MainWorkhorse.TimelineClip clip : track.getClips()) {
                Element clipNode = doc.createElement("clip");
                trackNode.appendChild(clipNode);

                // MediaItem path if not null (TEXT tracks don't have media items)
                if (clip.getMediaItem() != null) {
                    Element filePathNode = doc.createElement("filePath");
                    filePathNode.appendChild(doc.createTextNode(clip.getMediaItem().getFile().getAbsolutePath()));
                    clipNode.appendChild(filePathNode);
                }

                // General Clip properties
                addChildElement(doc, clipNode, "start", String.valueOf(clip.getStartSeconds()));
                addChildElement(doc, clipNode, "duration", String.valueOf(clip.getDurationSeconds()));
                addChildElement(doc, clipNode, "offset", String.valueOf(clip.getMediaStartOffset()));
                addChildElement(doc, clipNode, "speed", String.valueOf(clip.getPlaybackSpeed()));
                addChildElement(doc, clipNode, "opacity", String.valueOf(clip.getOpacity()));
                if (clip.getAliasName() != null) {
                    addChildElement(doc, clipNode, "aliasName", clip.getAliasName());
                }
                addChildElement(doc, clipNode, "isMuted", String.valueOf(clip.isMuted()));
                addChildElement(doc, clipNode, "scale", String.valueOf(clip.getScale()));
                addChildElement(doc, clipNode, "posX", String.valueOf(clip.getPosX()));
                addChildElement(doc, clipNode, "posY", String.valueOf(clip.getPosY()));

                // Text Clip properties
                if (track.getType() == MainWorkhorse.TrackType.TEXT) {
                    addChildElement(doc, clipNode, "textContent", clip.getTextContent());
                    addChildElement(doc, clipNode, "textColor", clip.getTextColor());
                    addChildElement(doc, clipNode, "fontSize", String.valueOf(clip.getFontSize()));
                }
            }
        }

        // Serialize XML to String in-memory
        StringWriter writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);

        String xmlString = writer.toString();

        // Encrypt the XML data
        byte[] encryptedBytes = encrypt(xmlString.getBytes(StandardCharsets.UTF_8));

        // Write binary bytes directly to the file
        Files.write(file.toPath(), encryptedBytes);
    }

    private static void addChildElement(Document doc, Element parent, String name, String value) {
        Element child = doc.createElement(name);
        child.appendChild(doc.createTextNode(value != null ? value : ""));
        parent.appendChild(child);
    }

    public static class LoadedProject {
        public List<File> mediaPoolFiles = new ArrayList<>();
        public List<LoadedTrack> tracks = new ArrayList<>();
    }

    public static class LoadedTrack {
        public String name;
        public String type;
        public List<LoadedClip> clips = new ArrayList<>();
    }

    public static class LoadedClip {
        public String filePath;
        public double start;
        public double duration;
        public double offset;
        public double speed = 1.0;
        public double opacity = 1.0;
        public String aliasName;
        public boolean isMuted = false;
        public double scale = 1.0;
        public double posX = 0.0;
        public double posY = 0.0;

        // Text properties
        public String textContent;
        public String textColor;
        public int fontSize = 48;
    }

    public static LoadedProject loadProject(File file) throws Exception {
        // Read raw encrypted bytes
        byte[] encryptedBytes = Files.readAllBytes(file.toPath());

        // Decrypt the bytes
        byte[] decryptedBytes = decrypt(encryptedBytes);

        // Parse XML string
        String xmlString = new String(decryptedBytes, StandardCharsets.UTF_8);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        StringReader reader = new StringReader(xmlString);
        InputSource is = new InputSource(reader);
        Document doc = db.parse(is);
        doc.getDocumentElement().normalize();

        LoadedProject project = new LoadedProject();

        // Parse Media Pool
        NodeList mediaPoolList = doc.getElementsByTagName("mediaPool");
        if (mediaPoolList != null && mediaPoolList.getLength() > 0) {
            Element mediaPoolEl = (Element) mediaPoolList.item(0);
            NodeList fileNodes = mediaPoolEl.getElementsByTagName("file");
            for (int i = 0; i < fileNodes.getLength(); i++) {
                Node node = fileNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    project.mediaPoolFiles.add(new File(node.getTextContent().trim()));
                }
            }
        }

        // Parse Tracks
        NodeList trackNodes = doc.getElementsByTagName("track");
        for (int i = 0; i < trackNodes.getLength(); i++) {
            Node trackNode = trackNodes.item(i);
            if (trackNode.getNodeType() == Node.ELEMENT_NODE) {
                Element trackEl = (Element) trackNode;
                LoadedTrack track = new LoadedTrack();
                track.name = trackEl.getAttribute("name");
                track.type = trackEl.getAttribute("type");
                project.tracks.add(track);

                NodeList clipNodes = trackEl.getElementsByTagName("clip");
                for (int j = 0; j < clipNodes.getLength(); j++) {
                    Node clipNode = clipNodes.item(j);
                    if (clipNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element clipEl = (Element) clipNode;
                        LoadedClip clip = new LoadedClip();

                        clip.filePath = getChildText(clipEl, "filePath");
                        clip.start = getDoubleChild(clipEl, "start", 0.0);
                        clip.duration = getDoubleChild(clipEl, "duration", 5.0);
                        clip.offset = getDoubleChild(clipEl, "offset", 0.0);
                        clip.speed = getDoubleChild(clipEl, "speed", 1.0);
                        clip.opacity = getDoubleChild(clipEl, "opacity", 1.0);
                        clip.aliasName = getChildText(clipEl, "aliasName");
                        clip.isMuted = getBooleanChild(clipEl, "isMuted", false);
                        clip.scale = getDoubleChild(clipEl, "scale", 1.0);
                        clip.posX = getDoubleChild(clipEl, "posX", 0.0);
                        clip.posY = getDoubleChild(clipEl, "posY", 0.0);

                        clip.textContent = getChildText(clipEl, "textContent");
                        clip.textColor = getChildText(clipEl, "textColor");
                        clip.fontSize = getIntChild(clipEl, "fontSize", 48);

                        track.clips.add(clip);
                    }
                }
            }
        }

        return project;
    }

    private static String getChildText(Element parent, String name) {
        NodeList nl = parent.getElementsByTagName(name);
        if (nl != null && nl.getLength() > 0) {
            Node n = nl.item(0);
            if (n != null) {
                return n.getTextContent().trim();
            }
        }
        return null;
    }

    private static double getDoubleChild(Element parent, String name, double defValue) {
        String txt = getChildText(parent, name);
        if (txt != null) {
            try {
                return Double.parseDouble(txt);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return defValue;
    }

    private static int getIntChild(Element parent, String name, int defValue) {
        String txt = getChildText(parent, name);
        if (txt != null) {
            try {
                return Integer.parseInt(txt);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return defValue;
    }

    private static boolean getBooleanChild(Element parent, String name, boolean defValue) {
        String txt = getChildText(parent, name);
        if (txt != null) {
            return Boolean.parseBoolean(txt);
        }
        return defValue;
    }
}
