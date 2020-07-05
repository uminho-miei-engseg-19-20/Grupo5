package code;

import org.apache.commons.lang3.StringUtils;
import java.io.*;

import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Scanner;

import wsdlservice.*;

public class CmdSoapMsg {

    final CCMovelDigitalSignature service = new CCMovelDigitalSignature();
    final CCMovelSignature connector = service.getBasicHttpBindingCCMovelSignature();

    public String getWsdl(int wsdl) {

        String[] wsdlURL = new String[] {
                "https://preprod.cmd.autenticacao.gov.pt/Ama.Authentication.Frontend/CCMovelDigitalSignature.svc?wsdl",
                "https://cmd.autenticacao.gov.pt/Ama.Authentication.Frontend/CCMovelDigitalSignature.svc?wsdl"
        };
        try {
            return wsdlURL[wsdl];
        } catch(ArrayIndexOutOfBoundsException exception) {
            return "No valid WSDL.";
        }
    }

    public byte[] hash(String message) throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        return digest.digest(message.getBytes());
    }

    public byte[] hashPrefix(byte[] hash, String hashType) {

        byte[] Sha256 = new byte[] {
                (byte)0x30, (byte)0x31, (byte)0x30, (byte)0x0d, (byte)0x06, (byte)0x09,
                (byte)0x60, (byte)0x86, (byte)0x48, (byte)0x01, (byte)0x65, (byte)0x03,
                (byte)0x04, (byte)0x02, (byte)0x01, (byte)0x05, (byte)0x00, (byte)0x04, (byte)0x20};

        // Concatenar o Prefixo à Hash dada como parâmetro
        byte[] hashWithPrefix = new byte[Sha256.length + hash.length];
        System.arraycopy(Sha256   , 0, hashWithPrefix, 0, Sha256.length);
        System.arraycopy(hash, 0, hashWithPrefix, Sha256.length, hash.length);

        return hashWithPrefix;
    }

    public void createPemFile(byte[] applicationId, String userId) {

        String certificate = getCertificate(applicationId,userId);
        OutputStream certificatePem = null;

        try {
            certificatePem = new FileOutputStream(new File("src/main/resources/" + StringUtils.substring(userId,5) + ".pem"));
            certificatePem.write(certificate.getBytes(), 0, certificate.length());
        } catch (IOException exception) {
            System.out.println("Unable to create PEM File.");
        } finally {
            try {
                assert certificatePem != null;
                certificatePem.close();
            } catch (IOException exception) {
                System.out.println("Unable to close PEM File.");
            }
        }
    }

    public KeyStore getCertChain(String userId) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {

        String aliasName = null;
        int numberPath = 0;

        InputStream pemFile = new FileInputStream("src/main/resources/" + StringUtils.substring(userId,5)  + ".pem");
        BufferedInputStream contentPemFile = new BufferedInputStream(pemFile);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);

        while (contentPemFile.available() > 0) {
            if(numberPath == 0) aliasName = "user";
            else if(numberPath == 1) aliasName = "root";
            else if(numberPath == 2) aliasName = "CA";

            Certificate certificate = cf.generateCertificate(contentPemFile);
            keyStore.setCertificateEntry(aliasName, certificate);

            numberPath++;
        }

        return keyStore;
    }

    public String getSubject(KeyStore certChain, String aliasName) throws KeyStoreException {

        String certificate = certChain.getCertificate(aliasName).toString();

        return StringUtils.substringBetween(certificate, "Subject: CN=", ",");
    }

    public String getCertificate(byte[] applicationId, String userId) {
        return connector.getCertificate(applicationId, userId);
    }

    public String ccMovelSign(byte[] applicationId, String docName, byte[] hash, String userId, String userPin) throws NoSuchAlgorithmException {

        // Criar a Instância do Pedido
        SignRequest request = new SignRequest();

        // Definir o Application Id
        request.setApplicationId(applicationId);

        // Definir o Document Name
        if(docName == null) request.setDocName("Documento Teste");
        else request.setDocName(docName);

        // Definir a Hash
        if(hash == null) {
            String message = "Nobody inspects the spammish repetition";
            byte[] encodedHash = hash(message);

            byte[] hashWithPrefix = hashPrefix(encodedHash, "Sha256");
            request.setHash(encodedHash);
        }
        else {
            byte[] hashWithPrefix = hashPrefix(hash, "Sha256");
            request.setHash(hash);
        }

        // Definir o Id e o Pin do User
        request.setUserId(userId);
        request.setPin(userPin);

        // Efetuar o pedido ao serviço AMA
        SignStatus status = connector.ccMovelSign(request);

        // Retornar apenas o processID para mostrar no menu CLI
        return status.getProcessId();
    }

    public String ccMovelMultipleSign(byte[] applicationId, String docName, byte[] hash, String userId, String userPin) throws NoSuchAlgorithmException {

        // Criar a Instância do Pedido
        MultipleSignRequest request = new MultipleSignRequest();

        // Definir o Application Id
        request.setApplicationId(applicationId);

        // Definir o Id e o Pin do User
        request.setUserId(userId);
        request.setPin(userPin);

        // Definir o Array de Documentos a Assinar
        ArrayOfHashStructure documents = new ArrayOfHashStructure();

        // Documento 1
        HashStructure firstDocument = new HashStructure();

        String firstMessage = "Nobody inspects the spammish repetition";
        byte[] firstHash = hash(firstMessage);

        firstDocument.setHash(firstHash);
        firstDocument.setName("Docname Test 1");
        firstDocument.setId("1234");

        documents.getHashStructure().add(firstDocument);

        // Documento 2
        HashStructure secondDocument = new HashStructure();

        String secondMessage = "Nobody inspects the spammish repetition";
        byte[] secondHash = hash(secondMessage);

        secondDocument.setHash(secondHash);
        secondDocument.setName("Docname Test 2");
        secondDocument.setId("1235");

        documents.getHashStructure().add(secondDocument);

        // Efetuar o pedido ao serviço AMA
        SignStatus status = connector.ccMovelMultipleSign(request, documents);

        // Retornar apenas o processID para mostrar no menu CLI
        return status.getProcessId();
    }

    public byte[] validateOtp(byte[] applicationId, String processId, String otpCode) {

        SignResponse response = connector.validateOtp(otpCode, processId, applicationId);

        // Imprimir o resultado da validação da assinatura
        System.out.println(response.getStatus().getMessage());

        // Retornar a assinatura para o menu CLI
        return response.getSignature();
    }

    public String testAll(byte[] applicationId, String docName, String userId, String userPin) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {

        // Inicialização Test All Commands
        System.out.println("Test Command Line Program (for Preprod/Prod Signature CMD (SOAP) version 1.6 technical specification)");
        System.out.println("Initializing Test of All Commands");

        // Leitura dos Argumentos da Linha de Comandos
        System.out.println("0% ...   Reading Arguments from the Command Line");
        System.out.println("         Document Name: " + docName + ", User Id: " + userId);

        // Obtenção do Certificado e da KeyStore com a Chain do Certificado
        System.out.println("10% ...  Contacting CMD SOAP Server for GetCertificate Operation");

        createPemFile(applicationId, userId);
        KeyStore certChain = getCertChain(userId);

        // Impressão das informações dos vários níveis da Chain do Certificado
        System.out.println("20% ...  Certificate Emitted for " + "\"" + getSubject(certChain, "user") + "\"");
        System.out.println("         by the Certification Entity " + "\"" + getSubject(certChain, "CA") + "\"");
        System.out.println("         in the Hierarchy of " + "\"" + getSubject(certChain, "root") + "\"");

        // Leitura do Documento
        System.out.println("30% ...  Reading the Document " + "\"" + docName + "\"");

        FileInputStream document = null;
        try {
            document = new FileInputStream(new File("src/main/resources/" + docName));
        } catch (IOException exception) {
            System.out.println("Unable to open Document " + "\"" + docName + "\"");
        } finally {
            try {
                assert document != null;
                document.close();
            } catch (IOException exception) {
                System.out.println("Unable to close Document " + "\"" + docName + "\"");
            }
        }
        BufferedInputStream contentDocument = new BufferedInputStream(document);

        // Criação Hash do Documento
        System.out.println("40% ...  Hashing the Document " + "\"" + docName + "\"");

        byte[] hashDocument = hash(contentDocument.toString());

        // Impressão da Hash
        System.out.println("50% ...  Generated Hash ");
        System.out.println("         " + Arrays.toString(hashDocument));

        // Contactar Servidor SOAP para a operação CCMovelSign
        System.out.println("60% ...  Contacting CMD SOAP Server for CCMovelSign Operation");
        String resultCcMovelSign = ccMovelSign(applicationId, docName, hashDocument, userId, userPin);

        // Impressão do Process Id devolvido na operação anterior
        String processId = StringUtils.substringBefore(resultCcMovelSign, "ValidateOTP\n");
        System.out.println("70% ...  Process Id returned by CCMovelSign Operation");
        System.out.println("         " + processId);

        // Validação da OTP
        Scanner myScanner = new Scanner(System.in);
        System.out.println("80% ...  Initializing OTP Validation\n");
        System.out.println("Enter the OTP received on your Device:");
        String otpCode = myScanner.nextLine();

        System.out.println("90% ...  Contacting CMD SOAP Server for ValidateOtp Operation");
        byte[] signature = validateOtp(applicationId, processId, otpCode);

        // Validação da Assinatura devolvida pela operação anterior
        System.out.println("100% ... Signature returned by ValidateOtp Operation");
        System.out.println("         " + Arrays.toString(signature));

        System.out.println("110% ... Validating Signature\n");

        // Digest a verificar com a chave pública RSA
        byte[] hashFile = hash(contentDocument.toString());

        // Inicializar a verficação da assinatura RSA com o certificado do user
        Signature sig = Signature.getInstance("NonewithRSA");
        try {
            PublicKey pubkey = certChain.getCertificate("user").getPublicKey();
            sig.initVerify(pubkey);
            sig.update(hashFile);
            if (sig.verify(signature)) {
                System.out.println("Assinatura verificada com sucesso.");
            }
            else {
                System.out.println("Este certificado de utilizador não assinou este documento.");
            }
        } catch (InvalidKeyException | SignatureException e) {
            System.out.println("The verification of the signature failed.");
            System.out.println(e.toString());
        }

        return "\n############################################ Test All Done ##############################################\n";
    }

}