package stork.feather.util;

import java.util.*;

/**
 * A mapping from file extensions to MIME types. This is based on nginx's MIME
 * type map.
 */
public class MimeTypeMap {
  private final static Map<String,String> map = new HashMap<String,String>();

  static {
    map.put("html", "text/html");
    map.put("htm", "text/html");
    map.put("shtml", "text/html");
    map.put("css", "text/css");
    map.put("xml", "text/xml");
    map.put("gif", "image/gif");
    map.put("jpg", "image/jpeg");
    map.put("jpeg", "image/jpeg");
    map.put("js", "application/javascript");
    map.put("atom", "application/atom+xml");
    map.put("rss", "application/rss+xml");
    map.put("mml", "text/mathml");
    map.put("txt", "text/plain");
    map.put("jad", "text/vnd.sun.j2me.app-descriptor");
    map.put("wml", "text/vnd.wap.wml");
    map.put("htc", "text/x-component");
    map.put("png", "image/png");
    map.put("tif", "image/tiff");
    map.put("tiff", "image/tiff");
    map.put("wbmp", "image/vnd.wap.wbmp");
    map.put("ico", "image/x-icon");
    map.put("jng", "image/x-jng");
    map.put("bmp", "image/x-ms-bmp");
    map.put("svg", "image/svg+xml");
    map.put("svgz", "image/svg+xml");
    map.put("webp", "image/webp");
    map.put("woff", "application/font-woff");
    map.put("jar", "application/java-archive");
    map.put("war", "application/java-archive");
    map.put("ear", "application/java-archive");
    map.put("json", "application/json");
    map.put("hqx", "application/mac-binhex40");
    map.put("doc", "application/msword");
    map.put("pdf", "application/pdf");
    map.put("ps", "application/postscript");
    map.put("eps", "application/postscript");
    map.put("ai", "application/postscript");
    map.put("rtf", "application/rtf");
    map.put("m3u8", "application/vnd.apple.mpegurl");
    map.put("xls", "application/vnd.ms-excel");
    map.put("eot", "application/vnd.ms-fontobject");
    map.put("ppt", "application/vnd.ms-powerpoint");
    map.put("wmlc", "application/vnd.wap.wmlc");
    map.put("kml", "application/vnd.google-earth.kml+xml");
    map.put("kmz", "application/vnd.google-earth.kmz");
    map.put("7z", "application/x-7z-compressed");
    map.put("cco", "application/x-cocoa");
    map.put("jardiff", "application/x-java-archive-diff");
    map.put("jnlp", "application/x-java-jnlp-file");
    map.put("run", "application/x-makeself");
    map.put("pl", "application/x-perl");
    map.put("pm", "application/x-perl");
    map.put("prc", "application/x-pilot");
    map.put("pdb", "application/x-pilot");
    map.put("rar", "application/x-rar-compressed");
    map.put("rpm", "application/x-redhat-package-manager");
    map.put("sea", "application/x-sea");
    map.put("swf", "application/x-shockwave-flash");
    map.put("sit", "application/x-stuffit");
    map.put("tcl", "application/x-tcl");
    map.put("tk", "application/x-tcl");
    map.put("der", "application/x-x509-ca-cert");
    map.put("pem", "application/x-x509-ca-cert");
    map.put("crt", "application/x-x509-ca-cert");
    map.put("xpi", "application/x-xpinstall");
    map.put("xhtml", "application/xhtml+xml");
    map.put("xspf", "application/xspf+xml");
    map.put("zip", "application/zip");
    map.put("bin", "application/octet-stream");
    map.put("exe", "application/octet-stream");
    map.put("dll", "application/octet-stream");
    map.put("deb", "application/octet-stream");
    map.put("dmg", "application/octet-stream");
    map.put("iso", "application/octet-stream");
    map.put("img", "application/octet-stream");
    map.put("msi", "application/octet-stream");
    map.put("msp", "application/octet-stream");
    map.put("msm", "application/octet-stream");
    map.put("mid", "audio/midi");
    map.put("midi", "audio/midi");
    map.put("kar", "audio/midi");
    map.put("mp3", "audio/mpeg");
    map.put("ogg", "audio/ogg");
    map.put("m4a", "audio/x-m4a");
    map.put("ra", "audio/x-realaudio");
    map.put("3gpp", "video/3gpp");
    map.put("3gp", "video/3gpp");
    map.put("ts", "video/mp2t");
    map.put("mp4", "video/mp4");
    map.put("mpeg", "video/mpeg");
    map.put("mpg", "video/mpeg");
    map.put("mov", "video/quicktime");
    map.put("webm", "video/webm");
    map.put("flv", "video/x-flv");
    map.put("m4v", "video/x-m4v");
    map.put("mng", "video/x-mng");
    map.put("asx", "video/x-ms-asf");
    map.put("asf", "video/x-ms-asf");
    map.put("wmv", "video/x-ms-wmv");
    map.put("avi", "video/x-msvideo");
  }

  public static String get(String ext) {
    return map.get(ext);
  }

  public static String forFile(String file) {
    String[] split = file.split("\\.");
    return (split.length == 0) ? null : get(split[split.length-1]);
  }
}
