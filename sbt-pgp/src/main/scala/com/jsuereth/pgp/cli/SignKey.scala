package bleep.plugin.pgp
package cli

case class SignKey(pubKey: String, notation: (String, String)) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val matches = for {
      ring <- ctx.publicKeyRing.keyRings.toSeq
      key <- ring.publicKeys
      if PGP.isPublicKeyMatching(pubKey)(key)
    } yield ring -> key
    val newpubringcol = matches match {
      case (ring, key) :: _ =>
        val signingKey = ctx.secretKeyRing.secretKey
        val newkey = ctx.withPassphrase(signingKey.keyID) { pw =>
          ctx.log.info("Signing key: " + key)
          try
            signingKey.signPublicKey(key, notation, pw)
          catch {
            case t: Throwable =>
              ctx.log.error("Error signing key!", t)
              throw t
          }
        }
        val newpubring = ring :+ newkey
        (ctx.publicKeyRing removeRing ring) :+ newpubring
      case Nil => sys.error("Could not find key: " + pubKey)
    }
    newpubringcol saveToFile ctx.publicKeyRingFile
  }
}
