// source: https://github.com/kubernetes-monitoring/kubernetes-mixin/blob/master/lib/utils.libsonnet
{
  mapRuleGroups(f): {
    groups: [
      group {
        rules: [
          f(rule)
          for rule in super.rules
        ],
      }
      for group in super.groups
    ],
  },
}
