package bleep.plugin.pgp

import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider

import java.io.*
import java.security.Security

/** This trait defines things that can act like a public key.  That is they can verify signed files and messages and encrypt data for an individual. */
trait PublicKeyLike {

  /** Verifies a signed message and extracts the contents.
    * @param input
    *   The incoming PGP message.
    * @param output
    *   The decoded and verified message.
    */
  def verifyMessageStream(input: InputStream, output: OutputStream): Boolean

  /** Reads in a PGP message from a file, verifies the signature and writes to the output file. */
  final def verifyMessageFile(input: File, output: File): Boolean = {
    val in = new FileInputStream(input)
    val out = new FileOutputStream(output)
    try verifyMessageStream(in, out)
    finally {
      in.close()
      out.close()
    }
  }

  /** Reads in a PGP message  and from a string, verifies the signature and returns the raw content. */
  final def verifyMessageString(input: String): String = {
    // TODO - better encoding support here.
    val in = new java.io.ByteArrayInputStream(input.getBytes())
    val out = new java.io.ByteArrayOutputStream()
    // TODO - better errors...
    assert(verifyMessageStream(in, out))
    out.toString(java.nio.charset.Charset.defaultCharset.name)
  }

  /** Verifies a signature stream against an input stream.
    * @param msgName
    *   the name tied in the signature for this object. For a file, this is the filename.
    * @param msg
    *   The input stream containing the raw message to verify.
    * @param signature
    *   The input stream containing the PGP signature.
    */
  def verifySignatureStreams(msg: InputStream, signature: InputStream): Boolean

  /** Reads in a raw file, verifies the signature file is valid for this file. */
  final def verifySignatureFile(raw: File, signature: File): Boolean = {
    val in = new FileInputStream(raw)
    val in2 = new FileInputStream(signature)
    try verifySignatureStreams(in, in2)
    finally {
      in.close()
      in2.close()
    }
  }

  /** Reads in a PGP message from a string, verifies the signature string is accurate for the message. */
  final def verifySignatureString(msg: String, signature: String): Boolean = {
    // TODO - better encoding support here.
    val in = new java.io.ByteArrayInputStream(msg.getBytes)
    val in2 = new java.io.ByteArrayInputStream(signature.getBytes)
    // TODO - better errors...
    try verifySignatureStreams(in, in2)
    finally {
      in.close()
      in2.close()
    }
  }

  protected def verifyMessageStreamHelper(input: InputStream, output: OutputStream)(
      getKey: Long => PGPPublicKey
  ): Boolean = {
    val in = PGPUtil.getDecoderStream(input)
    val pgpFact = {
      val tmp = new JcaPGPObjectFactory(in)
      val c1 = tmp.nextObject().asInstanceOf[PGPCompressedData]
      new JcaPGPObjectFactory(c1.getDataStream())
    }
    val sigList = pgpFact.nextObject().asInstanceOf[PGPOnePassSignatureList]
    val ops = sigList.get(0)
    val p2 = pgpFact.nextObject().asInstanceOf[PGPLiteralData]
    val dIn = p2.getInputStream()
    val key = getKey(ops.getKeyID)
    ops.init(new JcaPGPContentVerifierBuilderProvider().setProvider(Security.getProvider("BC")), key)
    var ch = dIn.read()
    while (ch >= 0) {
      ops.update(ch.asInstanceOf[Byte])
      output.write(ch)
      ch = dIn.read()
    }
    val p3 = pgpFact.nextObject().asInstanceOf[PGPSignatureList]
    ops.verify(p3.get(0))
  }

  /** Verifies that a stream was signed correctly by another stream.
    * @throws KeyNotFoundException
    *   is signature contains an unknown public key.
    */
  protected def verifySignatureStreamsHelper(msg: InputStream, signature: InputStream)(
      getKey: Long => PGPPublicKey
  ): Boolean = {
    val in = PGPUtil.getDecoderStream(signature)
    // We extract the signature list and object factory based on whether or not the signature is compressed.
    val (sigList, pgpFact @ _) = {
      val pgpFact = new JcaPGPObjectFactory(in)
      val o = pgpFact.nextObject()
      o match {
        case c1: PGPCompressedData =>
          (pgpFact.nextObject.asInstanceOf[PGPSignatureList], new JcaPGPObjectFactory(c1.getDataStream()))
        case sigList: PGPSignatureList => (sigList, pgpFact)
        case other                     => sys.error(s"Unexepected: $other")
      }
    }
    val dIn = new BufferedInputStream(msg)
    val sig = sigList.get(0)
    getKey(sig.getKeyID()) match {
      // TODO - special return for key not found.
      case null => throw KeyNotFoundException(sig.getKeyID())
      case key =>
        sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider(Security.getProvider("BC")), key)
        var ch = dIn.read()
        while (ch >= 0) {
          sig.update(ch.asInstanceOf[Byte])
          ch = dIn.read()
        }
        dIn.close()
        sig.verify()
    }
  }
}
