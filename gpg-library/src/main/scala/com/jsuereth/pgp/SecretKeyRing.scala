package bleep.plugin.pgp

import org.bouncycastle.bcpg.*
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

import java.io.*
import scala.jdk.CollectionConverters.*

/** A secret PGP key ring. Can be used to decrypt messages and to sign files/messages. */
class SecretKeyRing(val nested: PGPSecretKeyRing) extends StreamingSaveable {

  def extraPublicKeys: Iterator[PublicKey] =
    nested.getExtraPublicKeys.asScala.map(PublicKey.apply)

  def secretKeys: Iterator[SecretKey] =
    nested.getSecretKeys.asScala.map(SecretKey.apply)

  /** Looks for a secret key with the given id on this key ring. */
  def get(id: Long): Option[SecretKey] = secretKeys find (_.keyID == id)

  /** Gets the secret key with a given id from this key ring or throws. */
  def apply(id: Long): SecretKey = get(id).getOrElse(sys.error("Could not find secret key: " + id))

  /** The default public key for this key ring. */
  def publicKey = PublicKey(nested.getPublicKey)

  /** Returns the default secret key for this ring. */
  def secretKey = SecretKey(nested.getSecretKey)

  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)
    nested.encode(armoredOut)
    armoredOut.close()
  }

  override def toString = "SecretKeyRing(public=" + publicKey + ",secret=" + secretKeys.mkString(",") + ")"
}

object SecretKeyRing extends StreamingLoadable[SecretKeyRing] {
  def apply(ring: PGPSecretKeyRing) = new SecretKeyRing(ring)
  // TODO - Another way of generating SecretKeyRing from SecretKey objects.
  def load(input: InputStream) =
    apply(new PGPSecretKeyRing(PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator()))

  /** Creates a new secret key. */
  def create(identity: String, passPhrase: Array[Char]) =
    apply(KeyGen.makeElGamalKeyRingGenerator(identity, passPhrase).generateSecretKeyRing())
}
