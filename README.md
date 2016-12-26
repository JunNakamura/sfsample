# What is this ?

 Force.com APIを使ったサンプルコード

# 事前準備

* https://developer.salesforce.com/signup で開発アカウントを作成
* ログイン後にセキュリティトークンを発行

# WSDLからのclient-side codeの生成

1. [Enterprise](https://ap4.salesforce.com/soap/wsdl.jsp?type=*)
2. [Partner](https://ap4.salesforce.com/soap/wsdl.jsp)
3. [Metadata](https://ap4.salesforce.com/services/wsdl/metadata)

の３つがある。上記リンクからwsdlをダウンロードする。

[force-wsc](https://github.com/forcedotcom/wsc) のソースをビルド or [maven](https://mvnrepository.com/artifact/com.force.api/force-wsc)から取得

`com.sforce.ws.tools.wsdlc` を実行することでstubコードをまとめたjarが生成される。

実行には以下が必要。
```
antlr-runtime-3.5.jar  force-wsc-39.0.0.jar  ST4-4.0.7.jar  stringtemplate-3.2.1.jar
```

上記をひとつのディレクトリ(libs)にまとめて、wsdlファイル、作成後のjarファイル名を渡す。

`java -cp 'libs/force-wsc-39.0.0.jar:libs/*' com.sforce.ws.tools.wsdlc sfsample/src/resources/enterprise.wsdl  enterprize.jar`


# 接続情報

開発アカウントのID(メールアドレス)、パスワード、セキュリティトークンをシステムプロパティ経由で渡す

`-DSF_USER=<user_id> -DSF_PASSWORD=<password+security_token> `

# 参考サイト

* https://developer.salesforce.com/page/Introduction_to_the_Force.com_Web_Services_Connector