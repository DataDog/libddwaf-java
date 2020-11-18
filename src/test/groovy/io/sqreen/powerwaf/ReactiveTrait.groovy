package io.sqreen.powerwaf

trait ReactiveTrait extends PowerwafTrait {

    static final String TEST_REACTIVE_RULE = '''
        {"rules": [{"rule_id": "rule_931110", "filters": [{"operator": "@rx", "targets": ["REQUEST_BODY-0"], "transformations": [], "value": "(?:\\\\binclude\\\\s*\\\\([^)]*|mosConfig_absolute_path|_CONF\\\\[path\\\\]|_SERVER\\\\[DOCUMENT_ROOT\\\\]|GALLERY_BASEDIR|path\\\\[docroot\\\\]|appserv_root|config\\\\[root_dir\\\\])=(?:file|ftps?|https?):\\\\/\\\\/", "options": {"case_sensitive": false, "min_length": 15}}]}, {"rule_id": "rule_931120", "filters": [{"operator": "@rx", "targets": ["ARGS-0", "ARGS-1"], "transformations": [], "value": "^(?i:file|ftps?|https?).*?\\\\?+$", "options": {"case_sensitive": true, "min_length": 4}}]}, {"rule_id": "rule_933140", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "php://(?:std(?:in|out|err)|(?:in|out)put|fd|memory|temp|filter)", "options": {"case_sensitive": false, "min_length": 8}}]}, {"rule_id": "rule_933150", "filters": [{"operator": "@pm", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["lowercase"], "value": ["__halt_compiler", "apache_child_terminate", "base64_decode", "bzdecompress", "call_user_func", "call_user_func_array", "call_user_method", "call_user_method_array", "convert_uudecode", "file_get_contents", "file_put_contents", "fsockopen", "get_class_methods", "get_class_vars", "get_defined_constants", "get_defined_functions", "get_defined_vars", "gzdecode", "gzinflate", "gzuncompress", "include_once", "invokeargs", "pcntl_exec", "pcntl_fork", "pfsockopen", "posix_getcwd", "posix_getpwuid", "posix_getuid", "posix_uname", "reflectionfunction", "require_once", "shell_exec", "str_rot13", "sys_get_temp_dir", "wp_remote_fopen", "wp_remote_get", "wp_remote_head", "wp_remote_post", "wp_remote_request", "wp_safe_remote_get", "wp_safe_remote_head", "wp_safe_remote_post", "wp_safe_remote_request", "zlib_decode"], "options": {}}]}, {"rule_id": "rule_933160", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "\\\\b(?:s(?:e(?:t(?:_(?:e(?:xception|rror)_handler|magic_quotes_runtime|include_path)|defaultstub)|ssion_s(?:et_save_handler|tart))|qlite_(?:(?:(?:unbuffered|single|array)_)?query|create_(?:aggregate|function)|p?open|exec)|tr(?:eam_(?:context_create|socket_client)|ipc?slashes|rev)|implexml_load_(?:string|file)|ocket_c(?:onnect|reate)|h(?:ow_sourc|a1_fil)e|pl_autoload_register|ystem)|p(?:r(?:eg_(?:replace(?:_callback(?:_array)?)?|match(?:_all)?|split)|oc_(?:(?:terminat|clos|nic)e|get_status|open)|int_r)|o(?:six_(?:get(?:(?:e[gu]|g)id|login|pwnam)|mk(?:fifo|nod)|ttyname|kill)|pen)|hp(?:_(?:strip_whitespac|unam)e|version|info)|g_(?:(?:execut|prepar)e|connect|query)|a(?:rse_(?:ini_file|str)|ssthru)|utenv)|r(?:unkit_(?:function_(?:re(?:defin|nam)e|copy|add)|method_(?:re(?:defin|nam)e|copy|add)|constant_(?:redefine|add))|e(?:(?:gister_(?:shutdown|tick)|name)_function|ad(?:(?:gz)?file|_exif_data|dir))|awurl(?:de|en)code)|i(?:mage(?:createfrom(?:(?:jpe|pn)g|x[bp]m|wbmp|gif)|(?:jpe|pn)g|g(?:d2?|if)|2?wbmp|xbm)|s_(?:(?:(?:execut|write?|read)ab|fi)le|dir)|ni_(?:get(?:_all)?|set)|terator_apply|ptcembed)|g(?:et(?:_(?:c(?:urrent_use|fg_va)r|meta_tags)|my(?:[gpu]id|inode)|(?:lastmo|cw)d|imagesize|env)|z(?:(?:(?:defla|wri)t|encod|fil)e|compress|open|read)|lob)|a(?:rray_(?:u(?:intersect(?:_u?assoc)?|diff(?:_u?assoc)?)|intersect_u(?:assoc|key)|diff_u(?:assoc|key)|filter|reduce|map)|ssert(?:_options)?)|h(?:tml(?:specialchars(?:_decode)?|_entity_decode|entities)|(?:ash(?:_(?:update|hmac))?|ighlight)_file|e(?:ader_register_callback|x2bin))|f(?:i(?:le(?:(?:[acm]tim|inod)e|(?:_exist|perm)s|group)?|nfo_open)|tp_(?:nb_(?:ge|pu)|connec|ge|pu)t|(?:unction_exis|pu)ts|write|open)|o(?:b_(?:get_(?:c(?:ontents|lean)|flush)|end_(?:clean|flush)|clean|flush|start)|dbc_(?:result(?:_all)?|exec(?:ute)?|connect)|pendir)|m(?:b_(?:ereg(?:_(?:replace(?:_callback)?|match)|i(?:_replace)?)?|parse_str)|(?:ove_uploaded|d5)_file|ethod_exists|ysql_query|kdir)|e(?:x(?:if_(?:t(?:humbnail|agname)|imagetype|read_data)|ec)|scapeshell(?:arg|cmd)|rror_reporting|val)|c(?:url_(?:file_create|exec|init)|onvert_uuencode|reate_function|hr)|u(?:n(?:serialize|pack)|rl(?:de|en)code|[ak]?sort)|(?:json_(?:de|en)cod|debug_backtrac|tmpfil)e|b(?:(?:son_(?:de|en)|ase64_en)code|zopen)|var_dump)(?:\\\\s|/\\\\*.*\\\\*/|//.*|#.*)*\\\\(.*\\\\)", "options": {"case_sensitive": false, "min_length": 5}}]}, {"rule_id": "rule_933170", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS-0", "ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "[oOcC]:\\\\d+:\\\\\\".+?\\\\\\":\\\\d+:{.*}", "options": {"case_sensitive": true, "min_length": 12}}]}, {"rule_id": "rule_933200", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "(?i:zlib|glob|phar|ssh2|rar|ogg|expect|zip)://", "options": {"case_sensitive": true, "min_length": 6}}]}, {"rule_id": "rule_913110", "filters": [{"operator": "@pm", "targets": ["REQUEST_HEADERS_NAMES-0", "REQUEST_HEADERS-0"], "transformations": ["lowercase"], "value": ["acunetix-product", "(acunetix web vulnerability scanner", "acunetix-scanning-agreement", "acunetix-user-agreement", "myvar=1234", "x-ratproxy-loop", "bytes=0-,5-0,5-1,5-2,5-3,5-4,5-5,5-6,5-7,5-8,5-9,5-10,5-11,5-12,5-13,5-14"], "options": {}}]}, {"rule_id": "rule_913120", "filters": [{"operator": "@pm", "targets": ["ARGS-0", "ARGS-1"], "transformations": ["lowercase"], "value": ["/.adsensepostnottherenonobook", "/<invalid>hello.html", "/actsensepostnottherenonotive", "/acunetix-wvs-test-for-some-inexistent-file", "/antidisestablishmentarianism", "/appscan_fingerprint/mac_address", "/arachni-", "/cybercop", "/nessus_is_probing_you_", "/nessustest", "/netsparker-", "/rfiinc.txt", "/thereisnowaythat-you-canbethere", "/w3af/remotefileinclude.html", "appscan_fingerprint", "w00tw00t.at.isc.sans.dfind", "w00tw00t.at.blackhats.romanian.anti-sec"], "options": {}}]}, {"rule_id": "rule_942290", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:(?:\\\\[\\\\$(?:ne|eq|lte?|gte?|n?in|mod|all|size|exists|type|slice|x?or|div|like|between|and)\\\\]))", "options": {"case_sensitive": true, "min_length": 5}}]}, {"rule_id": "rule_932160", "filters": [{"operator": "@pm", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["lowercase"], "value": ["${cdpath}", "${dirstack}", "${home}", "${hostname}", "${ifs}", "${oldpwd}", "${ostype}", "${path}", "${pwd}", "$cdpath", "$dirstack", "$home", "$hostname", "$ifs", "$oldpwd", "$ostype", "$path", "$pwd", "bin/bash", "bin/cat", "bin/csh", "bin/dash", "bin/du", "bin/echo", "bin/grep", "bin/less", "bin/ls", "bin/mknod", "bin/more", "bin/nc", "bin/ps", "bin/rbash", "bin/sh", "bin/sleep", "bin/su", "bin/tcsh", "bin/uname", "dev/fd/", "dev/null", "dev/stderr", "dev/stdin", "dev/stdout", "dev/tcp/", "dev/udp/", "dev/zero", "etc/group", "etc/master.passwd", "etc/passwd", "etc/pwd.db", "etc/shadow", "etc/shells", "etc/spwd.db", "proc/self/", "usr/bin/awk", "usr/bin/base64", "usr/bin/cat", "usr/bin/cc", "usr/bin/clang", "usr/bin/clang++", "usr/bin/curl", "usr/bin/diff", "usr/bin/env", "usr/bin/fetch", "usr/bin/file", "usr/bin/find", "usr/bin/ftp", "usr/bin/gawk", "usr/bin/gcc", "usr/bin/head", "usr/bin/hexdump", "usr/bin/id", "usr/bin/less", "usr/bin/ln", "usr/bin/mkfifo", "usr/bin/more", "usr/bin/nc", "usr/bin/ncat", "usr/bin/nice", "usr/bin/nmap", "usr/bin/perl", "usr/bin/php", "usr/bin/php5", "usr/bin/php7", "usr/bin/php-cgi", "usr/bin/printf", "usr/bin/psed", "usr/bin/python", "usr/bin/python2", "usr/bin/python3", "usr/bin/ruby", "usr/bin/sed", "usr/bin/socat", "usr/bin/tail", "usr/bin/tee", "usr/bin/telnet", "usr/bin/top", "usr/bin/uname", "usr/bin/wget", "usr/bin/who", "usr/bin/whoami", "usr/bin/xargs", "usr/bin/xxd", "usr/bin/yes", "usr/local/bin/bash", "usr/local/bin/curl", "usr/local/bin/ncat", "usr/local/bin/nmap", "usr/local/bin/perl", "usr/local/bin/php", "usr/local/bin/python", "usr/local/bin/python2", "usr/local/bin/python3", "usr/local/bin/rbash", "usr/local/bin/ruby", "usr/local/bin/wget"], "options": {}}]}, {"rule_id": "rule_932170", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS-0"], "transformations": [], "value": "^\\\\(\\\\s*\\\\)\\\\s+{", "options": {"case_sensitive": true, "min_length": 4}}]}, {"rule_id": "rule_932171", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "^\\\\(\\\\s*\\\\)\\\\s+{", "options": {"case_sensitive": true, "min_length": 4}}]}, {"rule_id": "rule_934100", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?:(?:_(?:\\\\$\\\\$ND_FUNC\\\\$\\\\$_|_js_function)|(?:new\\\\s+Function|\\\\beval)\\\\s*\\\\(|String\\\\s*\\\\.\\\\s*fromCharCode|function\\\\s*\\\\(\\\\s*\\\\)\\\\s*{|this\\\\.constructor)|module\\\\.exports\\\\s*=)", "options": {"case_sensitive": true, "match_inter_transformers": true, "min_length": 5}}]}, {"rule_id": "rule_944100", "filters": [{"operator": "@rx", "targets": ["ARGS-0", "ARGS-1", "ARGS_NAMES-0", "ARGS_NAMES-1", "REQUEST_BODY-0", "REQUEST_HEADERS-0"], "transformations": ["lowercase"], "value": "java\\\\.lang\\\\.(?:runtime|processbuilder)", "options": {"case_sensitive": true, "min_length": 17}}]}, {"rule_id": "rule_944130", "filters": [{"operator": "@pm", "targets": ["ARGS-0", "ARGS-1", "ARGS_NAMES-0", "ARGS_NAMES-1", "REQUEST_BODY-0", "REQUEST_HEADERS-0"], "transformations": ["lowercase"], "value": ["com.opensymphony.xwork2", "com.sun.org.apache", "java.io.bufferedinputstream", "java.io.bufferedreader", "java.io.bytearrayinputstream", "java.io.bytearrayoutputstream", "java.io.chararrayreader", "java.io.datainputstream", "java.io.file", "java.io.fileoutputstream", "java.io.filepermission", "java.io.filewriter", "java.io.filterinputstream", "java.io.filteroutputstream", "java.io.filterreader", "java.io.inputstream", "java.io.inputstreamreader", "java.io.linenumberreader", "java.io.objectoutputstream", "java.io.outputstream", "java.io.pipedoutputstream", "java.io.pipedreader", "java.io.printstream", "java.io.pushbackinputstream", "java.io.reader", "java.io.stringreader", "java.lang.class", "java.lang.integer", "java.lang.number", "java.lang.object", "java.lang.process", "java.lang.processbuilder", "java.lang.reflect", "java.lang.runtime", "java.lang.string", "java.lang.stringbuilder", "java.lang.system", "javax.script.scriptenginemanager", "org.apache.commons", "org.apache.struts", "org.apache.struts2", "org.omg.corba", "java.beans.xmldecode"], "options": {}}]}, {"rule_id": "rule_sqreen_000001", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "(?i)^\\\\W*((http|ftp)s?://)?\\\\W*((::f{4}:)?(169|(0x)?0*a9|0+251)\\\\.?(254|(0x)?0*fe|0+376)[0-9a-fx\\\\.:]+|metadata\\\\.google\\\\.internal|metadata\\\\.goog)\\\\W*/", "options": {"case_sensitive": false, "min_length": 4}}]}, {"rule_id": "rule_sqreen_000002", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "require\\\\(['\\"][\\\\w\\\\.]+['\\"]\\\\)|process\\\\.\\\\w+\\\\([\\\\w\\\\.]*\\\\)|\\\\.toString\\\\(\\\\)", "options": {"case_sensitive": false, "min_length": 4}}]}, {"rule_id": "rule_sqreen_000007", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "REQUEST_HEADERS-0"], "transformations": [], "value": "\\\\$(eq|ne|lte?|gte?|n?in)\\\\b", "options": {}}]}, {"rule_id": "rule_sqreen_000008", "filters": [{"operator": "@rx", "targets": ["ARGS-0", "ARGS-1", "ARGS_NAMES-0", "ARGS_NAMES-1", "REQUEST_HEADERS-0"], "transformations": [], "value": "(?i)[&|]\\\\s*type\\\\s+%\\\\w+%\\\\\\\\+\\\\w+\\\\.ini\\\\s*[&|]", "options": {}}]}, {"rule_id": "rule_sqreen_000009", "filters": [{"operator": "@rx", "targets": ["ARGS-0", "ARGS-1", "ARGS_NAMES-0", "ARGS_NAMES-1", "REQUEST_HEADERS-0"], "transformations": [], "value": "(?i)[&|]\\\\s*cat\\\\s+\\\\/etc\\\\/[\\\\w\\\\.\\\\/]*passwd\\\\s*[&|]", "options": {}}]}, {"rule_id": "rule_sqreen_000010", "filters": [{"operator": "@rx", "targets": ["ARGS-0", "ARGS-1", "ARGS_NAMES-0", "ARGS_NAMES-1", "REQUEST_HEADERS-0"], "transformations": [], "value": "(?i)[&|]\\\\s*timeout\\\\s+/t\\\\s+\\\\d+\\\\s*[&|]", "options": {}}]}, {"rule_id": "rule_920210", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:Connection-0"], "transformations": [], "value": "\\\\b(?:keep-alive|close),\\\\s?(?:keep-alive|close)\\\\b", "options": {"case_sensitive": true, "min_length": 11}}]}, {"rule_id": "rule_920260", "filters": [{"operator": "@rx", "targets": ["REQUEST_URI-0", "REQUEST_BODY-0"], "transformations": [], "value": "\\\\%u[fF]{2}[0-9a-fA-F]{2}", "options": {"case_sensitive": true, "min_length": 6}}]}, {"rule_id": "rule_921140", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS_NAMES-0", "REQUEST_HEADERS-0"], "transformations": [], "value": "[\\\\n\\\\r]", "options": {"case_sensitive": true, "min_length": 1}}]}, {"rule_id": "rule_943100", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:\\\\.cookie\\\\b.*?;\\\\W*?(?:expires|domain)\\\\W*?=|\\\\bhttp-equiv\\\\W+set-cookie\\\\b)", "options": {"case_sensitive": true, "min_length": 15}}]}, {"rule_id": "rule_930100", "filters": [{"operator": "@rx", "targets": ["REQUEST_URI-0", "REQUEST_HEADERS-0"], "transformations": [], "value": "(?:\\\\x5c|(?:%(?:c(?:0%(?:[2aq]f|5c|9v)|1%(?:[19p]c|8s|af))|2(?:5(?:c(?:0%25af|1%259c)|2f|5c)|%46|f)|(?:(?:f(?:8%8)?0%8|e)0%80%a|bg%q)f|%3(?:2(?:%(?:%6|4)6|F)|5%%63)|u(?:221[56]|002f|EFC8|F025)|1u|5c)|0x(?:2f|5c)|\\\\/))(?:%(?:(?:f(?:(?:c%80|8)%8)?0%8|e)0%80%ae|2(?:(?:5(?:c0%25a|2))?e|%45)|u(?:(?:002|ff0)e|2024)|%32(?:%(?:%6|4)5|E)|c0(?:%[256aef]e|\\\\.))|\\\\.(?:%0[01]|\\\\?)?|\\\\?\\\\.?|0x2e){2}(?:\\\\x5c|(?:%(?:c(?:0%(?:[2aq]f|5c|9v)|1%(?:[19p]c|8s|af))|2(?:5(?:c(?:0%25af|1%259c)|2f|5c)|%46|f)|(?:(?:f(?:8%8)?0%8|e)0%80%a|bg%q)f|%3(?:2(?:%(?:%6|4)6|F)|5%%63)|u(?:221[56]|002f|EFC8|F025)|1u|5c)|0x(?:2f|5c)|\\\\/))", "options": {"case_sensitive": false, "min_length": 4}}]}, {"rule_id": "rule_930110", "filters": [{"operator": "@rx", "targets": ["REQUEST_URI-0", "REQUEST_HEADERS-0"], "transformations": ["removeNulls"], "value": "(?:^|[\\\\\\\\/])\\\\.\\\\.(?:[\\\\\\\\/]|$)", "options": {"case_sensitive": true, "match_inter_transformers": true, "min_length": 2}}]}, {"rule_id": "rule_941110", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0", "REQUEST_HEADERS:Referer-0", "ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "<script[^>]*>[\\\\s\\\\S]*?", "options": {"case_sensitive": false, "min_length": 8}}]}, {"rule_id": "rule_941140", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0", "REQUEST_HEADERS:Referer-0", "ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "(?:<(?:(?:apple|objec)t|isindex|embed|style|form|meta)\\\\b[^>]*?>[\\\\s\\\\S]*?|(?:=|U\\\\s*?R\\\\s*?L\\\\s*?\\\\()\\\\s*?[^>]*?\\\\s*?S\\\\s*?C\\\\s*?R\\\\s*?I\\\\s*?P\\\\s*?T\\\\s*?:)", "options": {"case_sensitive": false, "min_length": 6}}]}, {"rule_id": "rule_941200", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "(?i:<.*[:]?vmlframe.*?[\\\\s/+]*?src[\\\\s/+]*=)", "options": {"case_sensitive": true, "min_length": 13}}]}, {"rule_id": "rule_941210", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "(?i:(?:j|&#x?0*(?:74|4A|106|6A);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:a|&#x?0*(?:65|41|97|61);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:v|&#x?0*(?:86|56|118|76);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:a|&#x?0*(?:65|41|97|61);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:s|&#x?0*(?:83|53|115|73);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:c|&#x?0*(?:67|43|99|63);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:r|&#x?0*(?:82|52|114|72);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:i|&#x?0*(?:73|49|105|69);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:p|&#x?0*(?:80|50|112|70);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:t|&#x?0*(?:84|54|116|74);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?::|&(?:#x?0*(?:58|3A);?|colon;)).)", "options": {"case_sensitive": true, "min_length": 12}}]}, {"rule_id": "rule_941220", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "(?i:(?:v|&#x?0*(?:86|56|118|76);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:b|&#x?0*(?:66|42|98|62);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:s|&#x?0*(?:83|53|115|73);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:c|&#x?0*(?:67|43|99|63);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:r|&#x?0*(?:82|52|114|72);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:i|&#x?0*(?:73|49|105|69);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:p|&#x?0*(?:80|50|112|70);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?:t|&#x?0*(?:84|54|116|74);?)(?:\\\\t|&(?:#x?0*(?:9|13|10|A|D);?|tab;|newline;))*(?::|&(?:#x?0*(?:58|3A);?|colon;)).)", "options": {"case_sensitive": true, "min_length": 10}}]}, {"rule_id": "rule_941230", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "<EMBED[\\\\s/+].*?(?:src|type).*?=", "options": {"case_sensitive": false, "min_length": 11}}]}, {"rule_id": "rule_941240", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["lowercase", "removeNulls"], "value": "<[?]?import[\\\\s\\\\/+\\\\S]*?implementation[\\\\s\\\\/+]*?=", "options": {"case_sensitive": true, "min_length": 22}}]}, {"rule_id": "rule_941270", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "<LINK[\\\\s/+].*?href[\\\\s/+]*=", "options": {"case_sensitive": false, "min_length": 11}}]}, {"rule_id": "rule_941280", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "<BASE[\\\\s/+].*?href[\\\\s/+]*=", "options": {"case_sensitive": false, "min_length": 11}}]}, {"rule_id": "rule_941290", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "<APPLET[\\\\s/+>]", "options": {"case_sensitive": false, "min_length": 8}}]}, {"rule_id": "rule_941300", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": ["removeNulls"], "value": "<OBJECT[\\\\s/+].*?(?:type|codetype|classid|code|data)[\\\\s/+]*=", "options": {"case_sensitive": false, "min_length": 13}}]}, {"rule_id": "rule_941350", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "\\\\+ADw-.*(?:\\\\+AD4-|>)|<.*\\\\+AD4-", "options": {"case_sensitive": true, "min_length": 6}}]}, {"rule_id": "rule_941360", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "![!+ ]\\\\[\\\\]", "options": {"case_sensitive": true, "min_length": 4}}]}, {"rule_id": "rule_ua_6000", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "JDatabaseDriverMysqli", "options": {}}]}, {"rule_id": "rule_ua_6001", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "\\\\(\\\\) \\\\{ :; *\\\\}", "options": {}}]}, {"rule_id": "rule_ua_6002", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bqualys\\\\b", "options": {}}]}, {"rule_id": "rule_ua_6003", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bevilScanner\\\\b", "options": {}}]}, {"rule_id": "rule_ua_6004", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bcgichk\\\\b", "options": {}}]}, {"rule_id": "rule_ua_6005", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bbsqlbf\\\\b", "options": {}}]}, {"rule_id": "rule_ua_6006", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "mozilla/4\\\\.0 \\\\(compatible(; msie 6\\\\.0; win32)?\\\\)", "options": {}}]}, {"rule_id": "rule_ua_6007", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "^sqlmap/", "options": {}}]}, {"rule_id": "rule_ua_6009", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)mozilla/5\\\\.0 sf/", "options": {}}]}, {"rule_id": "rule_ua_60010", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)^Nessus(/|([ :]+SOAP))", "options": {}}]}, {"rule_id": "rule_ua_60012", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "^Arachni\\\\/v", "options": {}}]}, {"rule_id": "rule_ua_60013", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bJorgee\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60014", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bProbely\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60015", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bmetis\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60016", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "sql power injector", "options": {}}]}, {"rule_id": "rule_ua_60018", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bn-stealth\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60019", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bbrutus\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60020", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)(<script>netsparker\\\\(0x0|ns:netsparker.*=vuln)", "options": {}}]}, {"rule_id": "rule_ua_60022", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bjaascois\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60023", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bpmafind\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60025", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "webtrends security analyzer", "options": {}}]}, {"rule_id": "rule_ua_60026", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bnsauditor\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60027", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)Mozilla/.* Paros/", "options": {}}]}, {"rule_id": "rule_ua_60028", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bdirbuster\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60029", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bpangolin\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60030", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bsqlninja\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60031", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "\\\\(Nikto/[\\\\d\\\\.]+\\\\)", "options": {}}]}, {"rule_id": "rule_ua_60032", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bwebinspect\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60033", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bblack\\\\s?widow\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60034", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bgrendel-scan\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60035", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bhavij\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60036", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bw3af\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60037", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "nmap (nse|scripting engine)", "options": {}}]}, {"rule_id": "rule_ua_60039", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)^'?[a-z0-9]+\\\\.nasl'?$", "options": {}}]}, {"rule_id": "rule_ua_60040", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bWebFuck\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60041", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "md5\\\\(acunetix_wvs_security_test\\\\)", "options": {}}]}, {"rule_id": "rule_ua_60042", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)OpenVAS\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60043", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "Powered by Spider-Pig by tinfoilsecurity\\\\.com", "options": {}}]}, {"rule_id": "rule_ua_60044", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "Mozilla/\\\\d+.\\\\d+ zgrab", "options": {}}]}, {"rule_id": "rule_ua_60045", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bZmEu\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60046", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bcrowdstrike\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60047", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bGoogleSecurityScanner\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60048", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "^commix\\\\/", "options": {}}]}, {"rule_id": "rule_ua_60049", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "^gobuster\\\\/", "options": {}}]}, {"rule_id": "rule_ua_60051", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)^Fuzz Faster U Fool\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60052", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)^Nuclei\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60053", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bTsunamiSecurityScanner\\\\b", "options": {}}]}, {"rule_id": "rule_ua_60054", "filters": [{"operator": "@rx", "targets": ["REQUEST_HEADERS:User-Agent-0"], "transformations": [], "value": "(?i)\\\\bnimbostratus-bot\\\\b", "options": {}}]}, {"rule_id": "rule_942140", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:\\\\b(?:(?:m(?:s(?:ys(?:ac(?:cess(?:objects|storage|xml)|es)|(?:relationship|object|querie)s|modules2?)|db)|aster\\\\.\\\\.sysdatabases|ysql\\\\.db)|pg_(?:catalog|toast)|information_schema|northwind|tempdb)\\\\b|s(?:(?:ys(?:\\\\.database_name|aux)|qlite(?:_temp)?_master)\\\\b|chema(?:_name\\\\b|\\\\W*\\\\())|d(?:atabas|b_nam)e\\\\W*\\\\())", "options": {"case_sensitive": true, "min_length": 4}}]}, {"rule_id": "rule_942160", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:sleep\\\\(\\\\s*?\\\\d*?\\\\s*?\\\\)|benchmark\\\\(.*?\\\\,.*?\\\\))", "options": {"case_sensitive": true, "min_length": 7}}]}, {"rule_id": "rule_942220", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "^(?i:-0000023456|4294967295|4294967296|2147483648|2147483647|0000012345|-2147483648|-2147483649|0000023456|3.0.00738585072007e-308|1e309)$", "options": {"case_sensitive": true, "min_length": 5}}]}, {"rule_id": "rule_942240", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:(?:[\\\\\\"'`](?:;*?\\\\s*?waitfor\\\\s+(?:delay|time)\\\\s+[\\\\\\"'`]|;.*?:\\\\s*?goto)|alter\\\\s*?\\\\w+.*?cha(?:racte)?r\\\\s+set\\\\s+\\\\w+))", "options": {"case_sensitive": true, "min_length": 7}}]}, {"rule_id": "rule_942250", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:merge.*?using\\\\s*?\\\\(|execute\\\\s*?immediate\\\\s*?[\\\\\\"'`]|match\\\\s*?[\\\\w(?:),+-]+\\\\s*?against\\\\s*?\\\\()", "options": {"case_sensitive": true, "min_length": 11}}]}, {"rule_id": "rule_942270", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "union.*?select.*?from", "options": {"case_sensitive": false, "min_length": 15}}]}, {"rule_id": "rule_942280", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:(?:;\\\\s*?shutdown\\\\s*?(?:[#;]|\\\\/\\\\*|--|\\\\{)|waitfor\\\\s*?delay\\\\s?[\\\\\\"'`]+\\\\s?\\\\d|select\\\\s*?pg_sleep))", "options": {"case_sensitive": true, "min_length": 10}}]}, {"rule_id": "rule_942500", "filters": [{"operator": "@rx", "targets": ["ARGS_NAMES-0", "ARGS_NAMES-1", "ARGS-0", "ARGS-1"], "transformations": [], "value": "(?i:/\\\\*[!+](?:[\\\\w\\\\s=_\\\\-(?:)]+)?\\\\*/)", "options": {"case_sensitive": true, "min_length": 5}}]}], "flows": [{"name": "rfi-monitoring", "steps": [{"id": "start", "rule_ids": ["rule_931110"], "on_match": "exit_monitor"}]}, {"name": "rfi-blocking", "steps": [{"id": "start", "rule_ids": ["rule_931120"], "on_match": "exit_block"}]}, {"name": "php_eval-monitoring", "steps": [{"id": "start", "rule_ids": ["rule_933140", "rule_933150", "rule_933160", "rule_933170", "rule_933200"], "on_match": "exit_monitor"}]}, {"name": "security_scanner-blocking", "steps": [{"id": "start", "rule_ids": ["rule_913110", "rule_913120", "rule_ua_6000", "rule_ua_6001", "rule_ua_6002", "rule_ua_6003", "rule_ua_6004", "rule_ua_6005", "rule_ua_6006", "rule_ua_6007", "rule_ua_6009", "rule_ua_60010", "rule_ua_60012", "rule_ua_60013", "rule_ua_60014", "rule_ua_60015", "rule_ua_60016", "rule_ua_60018", "rule_ua_60019", "rule_ua_60020", "rule_ua_60022", "rule_ua_60023", "rule_ua_60025", "rule_ua_60026", "rule_ua_60027", "rule_ua_60028", "rule_ua_60029", "rule_ua_60030", "rule_ua_60031", "rule_ua_60032", "rule_ua_60033", "rule_ua_60034", "rule_ua_60035", "rule_ua_60036", "rule_ua_60037", "rule_ua_60039", "rule_ua_60040", "rule_ua_60041", "rule_ua_60042", "rule_ua_60043", "rule_ua_60044", "rule_ua_60045", "rule_ua_60046", "rule_ua_60047", "rule_ua_60048", "rule_ua_60049", "rule_ua_60051", "rule_ua_60052", "rule_ua_60053", "rule_ua_60054"], "on_match": "exit_block"}]}, {"name": "nosql_injection-monitoring", "steps": [{"id": "start", "rule_ids": ["rule_942290"], "on_match": "exit_monitor"}]}, {"name": "shell_injection-monitoring", "steps": [{"id": "start", "rule_ids": ["rule_932160", "rule_934100", "rule_944130", "rule_sqreen_000010"], "on_match": "exit_monitor"}]}, {"name": "shell_injection-blocking", "steps": [{"id": "start", "rule_ids": ["rule_932170", "rule_932171", "rule_944100", "rule_sqreen_000008", "rule_sqreen_000009"], "on_match": "exit_block"}]}, {"name": "Paranoid-blocking", "steps": [{"id": "start", "rule_ids": ["rule_sqreen_000001", "rule_sqreen_000002"], "on_match": "exit_block"}]}, {"name": "nosql_injection-blocking", "steps": [{"id": "start", "rule_ids": ["rule_sqreen_000007"], "on_match": "exit_block"}]}, {"name": "protocol-blocking", "steps": [{"id": "start", "rule_ids": ["rule_920210", "rule_920260", "rule_921140", "rule_943100"], "on_match": "exit_block"}]}, {"name": "lfi-blocking", "steps": [{"id": "start", "rule_ids": ["rule_930100", "rule_930110"], "on_match": "exit_block"}]}, {"name": "xss-blocking", "steps": [{"id": "start", "rule_ids": ["rule_941110", "rule_941140", "rule_941200", "rule_941210", "rule_941220", "rule_941230", "rule_941240", "rule_941270", "rule_941280", "rule_941290", "rule_941300", "rule_941350", "rule_941360"], "on_match": "exit_block"}]}, {"name": "sql_injection-monitoring", "steps": [{"id": "start", "rule_ids": ["rule_942140", "rule_942160", "rule_942220", "rule_942240", "rule_942250", "rule_942270", "rule_942280"], "on_match": "exit_monitor"}]}, {"name": "sql_injection-blocking", "steps": [{"id": "start", "rule_ids": ["rule_942500"], "on_match": "exit_block"}]}], "manifest": {"REQUEST_BODY-0": {"inherit_from": "server.request.body.raw", "run_on_key": false, "run_on_value": true}, "ARGS-0": {"inherit_from": "server.request.body", "run_on_key": false, "run_on_value": true}, "ARGS-1": {"inherit_from": "server.request.query", "run_on_key": false, "run_on_value": true}, "ARGS_NAMES-0": {"inherit_from": "server.request.body", "run_on_key": true, "run_on_value": false}, "ARGS_NAMES-1": {"inherit_from": "server.request.query", "run_on_key": true, "run_on_value": false}, "REQUEST_HEADERS-0": {"inherit_from": "server.request.headers.no_cookies", "run_on_key": false, "run_on_value": true}, "REQUEST_HEADERS_NAMES-0": {"inherit_from": "server.request.headers.no_cookies", "run_on_key": false, "run_on_value": true}, "REQUEST_HEADERS:Connection-0": {"inherit_from": "server.request.headers.no_cookies", "run_on_key": false, "run_on_value": true, "key_access": {"is_allowlist": true, "paths": [["connection"]]}}, "REQUEST_URI-0": {"inherit_from": "server.request.uri.raw", "run_on_key": false, "run_on_value": true, "processor": [{"transforms": ["urlDecode"]}]}, "REQUEST_HEADERS:User-Agent-0": {"inherit_from": "server.request.headers.no_cookies", "run_on_key": false, "run_on_value": true, "key_access": {"is_allowlist": true, "paths": [["user-agent"]]}}, "REQUEST_HEADERS:Referer-0": {"inherit_from": "server.request.headers.no_cookies", "run_on_key": false, "run_on_value": true, "key_access": {"is_allowlist": true, "paths": [["referer"]]}}}}
    '''
}