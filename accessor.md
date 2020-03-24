# ***JS简易存取器***

js因为有对象声明量，相对于其他语言，确实在构建对象时会极其简单与舒适

但是舒适的同时，也意味着当需要提供一些基本的，如Java HashMap的put、get方法时，需要通过其他方式去做非空判断。当不同的开发人员有不同的判断习惯，并加入到具体业务中，会产生相对应的歧义。

    const $ = require('underscore');

    const DEFAULT_KEY = 'default';

    class Accessor {
        constructor() {
            this.hash = {};
        }

        get(k) {
            if (!k)
                k = DEFAULT_KEY;
            let has = this.has(k);
            if (has)
                return this.hash[k];

            throw new Error(`Register: ${k}`);
        }

        getOrSet(k, v) {
            let has = this.has(k);
            if (has)
                return this.hash[k];

            return (this.hash[k] = v);
        }

        has(k) {
            return $.has(this.hash, k);
        }

        set(k, v) {
            if (!v) {
                v = k;
                k = DEFAULT_KEY;
            }

            this.hash[k] = v;
        }

        tryGet(k) {
            return this.hash[k || DEFAULT_KEY];
        }
    }

    module.exports = Accessor;

引入这个模块后，就可以直接使用啦