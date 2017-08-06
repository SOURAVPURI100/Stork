package stork.optimizers;

import stork.ad.*;
import stork.util.*;

// The optimizer base class. This optimizer can be initialized, but
// will simply tell the caller to transfer the whole file and use
// default transfer parameters.
//
// An optimizer, in short, after being initialized with required
// information, will output adsjdescribing parameters for a sampling.
// The client should parse the ad, run a sample transfer accordingly,
// and report requested information (generally just the throughput)
// back to the optimizer. The optimizer will use this information to
// generate the next sample, and so forth until the transfer is done.
//
// TODO: Optimizer chaining? Optimizer registration. Optimizer params.

public class Optimizer {
  // Return a Ad containing the parameters for a sample. The ad
  // returned here should indicate some kind of limit on the sample, be
  // it a time limit or a byte length. If none is present, or length is
  // specified to be -1, or null is returned, the caller should assume
  // the optimizer is done optimizing and finish the transfer.
  public Ad sample() {
    return new Ad();
  } 

  // Used to report the results of a sample back to the optimizer. If
  // null is passed, assume the sampling failed. The optimizer should
  // also be able to handle missing attributes.
  public void report(Ad ad) { }

  // Used to initialize the optimizer's starting settings.
  //public void initialize(Ad ad) { }
  public void initialize(long size, Range range) { }

  // Get the name of the optimizer.
  public String name() {
    return "none";
  }
} 
