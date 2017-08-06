package stork.feather.errors;

public class Redirect extends FeatherError {
  public final String url;

  public Redirect(String url) {
    //this();
    super("Redirect to: "+url);
    this.url = url;
  }
}
