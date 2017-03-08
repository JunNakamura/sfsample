# What is this ?

 Force.com APIを使ったサンプルコード. 
 主にsoap apiのサンプルを作成

# prerequisite

jdk 1.8 or later. 
 
# APIの種類

主なものは以下のもの

* REST API
* SOAP API
* Bulk API
* Metadata API
 
 
# どのAPIを使用すべきか

https://help.salesforce.com/articleView?id=integrate_what_is_api.htm&language=ja&type=0 

# SOAP API

http://resources.docs.salesforce.com/rel1/doc/en-us/static/pdf/SF_Soap_API_cheatsheet_web.pdf

## 事前準備

* https://developer.salesforce.com/signup で開発アカウントを作成
* ログイン後にセキュリティトークンを発行

### セキュリティトークンの発行

ログイン後、画面右上に、ユーザ名がある。そこから、 「私の設定」>「個人用」>「私のセキュリティートークンのリセット」　で、トークンのリセット画面に遷移

`_ui/system/security/ResetApiTokenEdit?retURL=%2Fui%2Fsetup%2FSetup%3Fsetupid%3DPersonalInfo&setupid=ResetApiToken`

## WSDLからのclient-side codeの生成

1. [Enterprise](https://ap4.salesforce.com/soap/wsdl.jsp?type=*)
2. [Partner](https://ap4.salesforce.com/soap/wsdl.jsp)
3. [Metadata](https://ap4.salesforce.com/services/wsdl/metadata)

の３つがある。上記リンクからwsdlをダウンロードする。(main/resourcesに保持している)
wsdlから追加されたカスタムオブジェクト、カスタムフィールドに対応するテーブルやカラムの情報を取得し、対応するJavaクラスが生成される。
(コードで指定しているオブジェクトがないときはsalesforceアプリにログインして適宜作成すればよい)

[force-wsc](https://github.com/forcedotcom/wsc) のソースをビルド or [maven](https://mvnrepository.com/artifact/com.force.api/force-wsc)から取得
(build.gradleに依存ライブラリとして記述しているので、それを使える状態にしている)

`com.sforce.ws.tools.wsdlc` を実行することでstubコードをまとめたjarが生成される。

実行には以下が必要。（gradleで依存ライブラリを取得した場合は ${USER_HOME}/.gradle/以下にある）
```
antlr-runtime-3.5.jar  force-wsc-39.0.0.jar  ST4-4.0.7.jar  stringtemplate-3.2.1.jar
```

上記をひとつのディレクトリ(libs)にまとめて、wsdlファイル、作成後のjarファイル名を渡す。

`java -cp 'libs/force-wsc-39.0.0.jar:libs/*' com.sforce.ws.tools.wsdlc sfsample/src/resources/enterprise.wsdl  enterprise.jar`


## 接続情報

開発アカウントのID(メールアドレス)、パスワード、セキュリティトークンをシステムプロパティ経由で渡す

`-DSF_USER=<user_id> -DSF_PASSWORD=<password+security_token> `

eclipseなら、実行>実行の構成>引数タブ>VM引数　に記述することで指定できる.


# REST API

## quick start

https://developer.salesforce.com/docs/atlas.ja-jp.api_rest.meta/api_rest/quickstart_oauth.htm
http://resources.docs.salesforce.com/rel1/doc/en-us/static/pdf/SF_Rest_API_cheatsheet_web.pdf

```
curl https://***instance_name***.salesforce.com/services/data/v20.0/ -H 'Authorization: Bearer access_token'
```

### アクセストークン（セッションID）の取得方法

```
curl https://login.salesforce.com/services/oauth2/token -d "grant_type=password" -d "client_id=myclientid" -d "client_secret=myclientsecret" 
    -d "username=mylogin@salesforce.com" -d "password=mypassword123456"
```

セッションIDなので、salesforceアプリにログイン後、sidというcookieの値が該当するので、それで取得してもよい。

# Bulk API

https://developer.salesforce.com/docs/atlas.ja-jp.api_asynch.meta/api_asynch/asynch_api_intro.htm

## 一括クエリ

https://developer.salesforce.com/docs/atlas.ja-jp.api_asynch.meta/api_asynch/asynch_api_using_bulk_query.htm

# tool

* https://soqlbuilder.herokuapp.com
* http://dataloader.io/
* https://schemalister.herokuapp.com
* http://sftoolkit.co/
* https://force-cli.heroku.com/
* https://developer.salesforce.com/ja/tools/forcecli

# tips

## データの一括削除

* https://help.salesforce.com/articleView?id=000193399&language=ja&type=1
* https://help.salesforce.com/articleView?id=inserting_updating_or_deleting_data.htm&language=ja&type=0

## 1リクエストあたりの処理件数　

* http://salesforce.stackexchange.com/questions/55096/can-i-only-update-200-records-at-once-with-soap-api-enterprise-wsdl-limitation

# 参考サイト

* https://developer.salesforce.com/page/Introduction_to_the_Force.com_Web_Services_Connector
* https://developer.salesforce.com/docs/atlas.en-us.api.meta/api/sforce_api_quickstart_intro.htm
* https://developer.salesforce.com/page/Cheat_Sheets
* https://developer.salesforce.com/page/Java
* https://developer.salesforce.com/docs/atlas.en-us.api_meta.meta/api_meta/meta_intro.htm
