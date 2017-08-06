package stork.feather.net;

import stork.feather.*;

/**
 * A {@code Codec} is a {@code Coder} that is reversible.
 */
public class Codec<A,B> {
  private final Coder<A,B> encoder;
  private final Coder<B,A> decoder;

  public Codec(Coder<A,B> encoder, Coder<B,A> decoder) {
    this.encoder = encoder;
    this.decoder = decoder;
  }

  public final Coder<A,B> encoder() { return encoder; }
  public final Coder<B,A> decoder() { return decoder; }

  /**
   * Return a flipped version of this codec. By default, this just returns a
   * new {@code Codec} with this codec's encoder and decoder objects swapped.
   */
  public Codec<B,A> flip() {
    final Codec<A,B> original = this;
    return new Codec<B,A>(decoder, encoder) {
      public Codec<A,B> flip() { return original; }
    };
  }

  /**
   * Wrap a {@code Coder<A,A>} so that it appears to be a {@code Coder<B,B>}.
   */
  public final Coder<B,B> wrap(Coder<A,A> coder) {
    return decoder.join(coder).join(encoder);
  }

  /**
   * Join this {@code Codec<A,B>} with a {@code Codec<B,C>} to create a {@code
   * Codec<A,C>}.
   */
  public final <C> Codec<A,C> join(Codec<B,C> bc) {
    return new Codec<A,C>(
      encoder.join(bc.encoder), bc.decoder.join(decoder)
    );
  }
}
